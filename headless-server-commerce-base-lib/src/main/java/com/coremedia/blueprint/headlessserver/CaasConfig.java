package com.coremedia.blueprint.headlessserver;

import com.coremedia.blueprint.base.caas.model.adapter.SettingsAdapterFactory;
import com.coremedia.blueprint.base.caas.model.adapter.StructAdapterFactory;
import com.coremedia.blueprint.base.settings.SettingsService;
import com.coremedia.caas.config.CaasGraphqlConfigurationProperties;
import com.coremedia.caas.link.GraphQLLink;
import com.coremedia.caas.model.ContentRoot;
import com.coremedia.caas.model.mapper.CompositeModelMapper;
import com.coremedia.caas.model.mapper.FilteringModelMapper;
import com.coremedia.caas.model.mapper.ModelMapper;
import com.coremedia.caas.model.mapper.ModelMappingPropertyAccessor;
import com.coremedia.caas.model.mapper.ModelMappingWiringFactory;
import com.coremedia.caas.schema.CoercingBigDecimal;
import com.coremedia.caas.schema.SchemaParser;
import com.coremedia.caas.search.id.CaasContentBeanIdScheme;
import com.coremedia.caas.service.cache.Weighted;
import com.coremedia.caas.spel.SpelDirectiveWiring;
import com.coremedia.caas.spel.SpelEvaluationStrategy;
import com.coremedia.caas.spel.SpelFunctions;
import com.coremedia.caas.web.CaasServiceConfigurationProperties;
import com.coremedia.caas.web.GraphiqlConfigurationProperties;
import com.coremedia.caas.web.persistedqueries.DefaultQueryNormalizer;
import com.coremedia.caas.web.persistedqueries.QueryNormalizer;
import com.coremedia.caas.web.view.ViewBySiteFilterDataFetcher;
import com.coremedia.caas.web.wiring.GraphQLInvocationImpl;
import com.coremedia.caas.wiring.CapStructPropertyAccessor;
import com.coremedia.caas.wiring.CompositeTypeNameResolver;
import com.coremedia.caas.wiring.CompositeTypeNameResolverProvider;
import com.coremedia.caas.wiring.ContentRepositoryWiringFactory;
import com.coremedia.caas.wiring.ContextInstrumentation;
import com.coremedia.caas.wiring.ConvertingDataFetcher;
import com.coremedia.caas.wiring.DataFetcherMappingInstrumentation;
import com.coremedia.caas.wiring.ExecutionTimeoutInstrumentation;
import com.coremedia.caas.wiring.FilteringDataFetcher;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.RemoteLinkWiringFactory;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolverWiringFactory;
import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.cap.struct.StructService;
import com.coremedia.id.IdScheme;
import com.coremedia.link.CompositeLinkComposer;
import com.coremedia.link.LinkComposer;
import com.coremedia.link.uri.UriLinkBuilder;
import com.coremedia.link.uri.UriLinkComposer;
import com.coremedia.springframework.customizer.CustomizerConfiguration;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import graphql.GraphQL;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;
import graphql.spring.web.servlet.ExecutionResultHandler;
import graphql.spring.web.servlet.GraphQLInvocation;
import org.apache.commons.io.IOUtils;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.coremedia.caas.web.CaasWebConfig.ATTRIBUTE_NAMES_TO_GQL_CONTEXT;
import static java.util.Collections.emptyList;

@Configuration
@EnableConfigurationProperties({
        CaasServiceConfigurationProperties.class,
        CaasGraphqlConfigurationProperties.class,
        GraphiqlConfigurationProperties.class,
})
@EnableWebMvc
@ComponentScan({
        "com.coremedia.caas",
        "com.coremedia.cap.undoc.common.spring"
})
@ImportResource({
        "classpath:/com/coremedia/blueprint/base/settings/impl/bpbase-settings-services.xml"
})
@Import({
        CustomizerConfiguration.class
})
public class CaasConfig implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(CaasConfig.class);
  private static final String OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX = "query-root:";
  private static final int TWENTY_FOUR_HOURS = 24 * 60 * 60;
  private static final int CORS_RESPONSE_MAX_AGE = TWENTY_FOUR_HOURS;

  private final CaasServiceConfigurationProperties caasServiceConfigurationProperties;
  private final CaasGraphqlConfigurationProperties caasGraphqlConfigurationProperties;
  private final GraphiqlConfigurationProperties graphiqlConfigurationProperties;

  public CaasConfig(CaasServiceConfigurationProperties caasServiceConfigurationProperties,
                    CaasGraphqlConfigurationProperties caasGraphqlConfigurationProperties,
                    GraphiqlConfigurationProperties graphiqlConfigurationProperties) {
    this.caasServiceConfigurationProperties = caasServiceConfigurationProperties;
    this.caasGraphqlConfigurationProperties = caasGraphqlConfigurationProperties;
    this.graphiqlConfigurationProperties = graphiqlConfigurationProperties;
  }

  @Override
  public void configurePathMatch(PathMatchConfigurer matcher) {
    matcher.setUseSuffixPatternMatch(false);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowCredentials(true)
            .maxAge(CORS_RESPONSE_MAX_AGE);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    List<String> resources = new ArrayList<>(Arrays.asList("/static/**", "/docs/**"));
    List<String> resourceLocations = new ArrayList<>();
    if (graphiqlConfigurationProperties.isEnabled()) {
      resources.add("/graphiql/static/**");
      resourceLocations.add("classpath:/static/docs/");
    }
    if (caasServiceConfigurationProperties.getSwagger().isEnabled()) {
      resources.add("swagger-ui.html");
      resources.add("/webjars/**");
      resourceLocations.add("classpath:/META-INF/resources/webjars/");
    }
    if (caasServiceConfigurationProperties.isPreview()) {
      resourceLocations.add("classpath:/static/");
      resourceLocations.add("classpath:/META-INF/resources/");
    }
    registry.addResourceHandler(resources.toArray(new String[0]))
            .addResourceLocations(resourceLocations.toArray(new String[0]));
  }

  @Bean
  @ConditionalOnProperty("caas.logRequests")
  public Filter logFilter() {
    CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter() {
      @Override
      protected boolean shouldLog(HttpServletRequest request) {
        return true;
      }

      @Override
      protected void beforeRequest(HttpServletRequest request, @Nullable String message) {
        if (!RequestMethod.OPTIONS.name().equals(request.getMethod())) {
          LOG.trace(message);
        }
      }
    };
    filter.setIncludeQueryString(true);
    filter.setIncludePayload(false);
    return filter;
  }

  @Bean("cacheManager")
  @SuppressWarnings("unchecked")
  public CacheManager cacheManager() {
    ImmutableList.Builder<org.springframework.cache.Cache> builder = ImmutableList.builder();
    caasServiceConfigurationProperties.getCacheSpecs().forEach((cacheName, cacheSpec) -> {
      com.github.benmanes.caffeine.cache.Cache cache = Caffeine.from(cacheSpec)
              .weigher((key, value) -> {
                if (value instanceof Weighted) {
                  return ((Weighted) value).getWeight();
                }
                return String.valueOf(value).length();
              })
              .build();
      builder.add(new CaffeineCache(cacheName, cache));
    });
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(builder.build());
    return cacheManager;
  }

  @Bean
  public LinkComposer<Object, GraphQLLink> graphQlLinkComposer(List<LinkComposer<?, ? extends GraphQLLink>> linkComposers) {
    return new CompositeLinkComposer<>(linkComposers, emptyList());
  }

  @Bean
  public LinkComposer<Object, String> uriLinkComposer(List<LinkComposer<?, ? extends UriLinkBuilder>> linkComposers) {
    return new UriLinkComposer<>(
            new CompositeLinkComposer<>(linkComposers, emptyList()));
  }

  @Bean
  public TypeNameResolver<Content> contentTypeNameResolver(@Lazy GraphQLSchema schema) {
    return content -> Optional.of("Content_Impl");
  }

  @Bean
  public ProvidesTypeNameResolver providesContentTypeNameResolver() {
    return typeName -> "Content_".contains(typeName)
            ? Optional.of(true)
            : Optional.empty();
  }

  @Bean
  public TypeNameResolver<Object> compositeTypeNameResolver(List<TypeNameResolver<?>> typeNameResolvers) {
    return new CompositeTypeNameResolver<>(typeNameResolvers);
  }

  @Bean
  public ProvidesTypeNameResolver compositeProvidesTypeNameResolver(List<ProvidesTypeNameResolver> providesTypeNameResolvers) {
    return new CompositeTypeNameResolverProvider(providesTypeNameResolvers);
  }

  @Bean
  public TypeNameResolverWiringFactory typeNameResolverWiringFactory(
          @Qualifier("compositeProvidesTypeNameResolver") ProvidesTypeNameResolver providesTypeNameResolver,
          @Qualifier("compositeTypeNameResolver") TypeNameResolver<Object> typeNameResolver) {
    return new TypeNameResolverWiringFactory(providesTypeNameResolver, typeNameResolver);
  }

  @Bean
  public SettingsAdapterFactory settingsAdapter(SettingsService settingsService, StructAdapterFactory structAdapterFactory) {
    return new SettingsAdapterFactory(settingsService, structAdapterFactory);
  }

  @Bean
  public StructAdapterFactory structAdapter(StructService structService, SettingsService settingsService) {
    return new StructAdapterFactory(structService, settingsService);
  }

  @Bean
  public IdScheme caasContentBeanIdScheme(ContentRepository contentRepository) {
    return new CaasContentBeanIdScheme(contentRepository);
  }

  @Bean
  public List<IdScheme> idSchemes(IdScheme caasContentBeanIdScheme) {
    return Collections.singletonList(caasContentBeanIdScheme);
  }

  @Bean
  public ModelMapper<GregorianCalendar, ZonedDateTime> dateModelMapper() {
    return gregorianCalendar -> Optional.of(gregorianCalendar.toZonedDateTime());
  }

  @Bean
  public PropertyAccessor mapPropertyAccessor() {
    return new MapAccessor();
  }

  @Bean
  public PropertyAccessor reflectivePropertyAccessor() {
    return new ReflectivePropertyAccessor();
  }

  @Bean
  public PropertyAccessor capStructPropertyAccessor() {
    return new CapStructPropertyAccessor();
  }

  @Bean
  @Qualifier("propertyAccessors")
  public List<PropertyAccessor> propertyAccessors(List<PropertyAccessor> propertyAccessors, ModelMapper<Object, Object> modelMapper) {
    return propertyAccessors.stream()
            .map(propertyAccessor -> new ModelMappingPropertyAccessor(propertyAccessor, modelMapper))
            .collect(Collectors.toList());
  }

  @Bean
  public SpelEvaluationStrategy spelEvaluationStrategy(BeanFactory beanFactory, @Qualifier("propertyAccessors") List<PropertyAccessor> propertyAccessors) {
    return new SpelEvaluationStrategy(beanFactory, propertyAccessors);
  }

  @Bean
  @Qualifier("globalSpelVariables")
  public Method first() throws NoSuchMethodException {
    return SpelFunctions.class.getDeclaredMethod("first", List.class);
  }

  @Bean
  public SchemaDirectiveWiring fetch(SpelEvaluationStrategy spelEvaluationStrategy,
                                     @Qualifier("globalSpelVariables") Map<String, Object> globalSpelVariables) {
    return new SpelDirectiveWiring(spelEvaluationStrategy, globalSpelVariables);
  }

  @Bean
  public ConversionServiceFactoryBean graphQlConversionService(Set<Converter> converters) {
    ConversionServiceFactoryBean conversionServiceFactoryBean = new ConversionServiceFactoryBean();
    conversionServiceFactoryBean.setConverters(converters);
    return conversionServiceFactoryBean;
  }

  @Bean
  public ContentRepositoryWiringFactory contentRepositoryWiringFactory(Map<String, GraphQLScalarType> builtinScalars,
                                                                       ContentRepository repository) {
    return new ContentRepositoryWiringFactory(repository, builtinScalars);
  }

  @Bean
  public RemoteLinkWiringFactory remoteLinkWiringFactory() {
    return new RemoteLinkWiringFactory();
  }

  /**
   * Only as example to plugin a filter predicate.
   * Previously used to check validation date ins content is in production.
   */
  @Bean
  @Qualifier("filterPredicate")
  public Predicate<Object> validityDateFilterPredicate() {
    return o -> true;
  }

  @Bean
  public ModelMapper<Object, Object> rootModelMapper(List<ModelMapper<?, ?>> modelMappers, @Qualifier("filterPredicate") List<Predicate<Object>> predicates) {
    return new FilteringModelMapper(new CompositeModelMapper<>(modelMappers), predicates);
  }

  @Bean
  @Qualifier("queryRoot")
  public ContentRoot content(ContentRepository repository, SitesService sitesService) {
    return new ContentRoot(repository, sitesService);
  }

  @Bean
  public GraphQLInvocation graphQLInvocation(GraphQL graphQL,
                                             @Qualifier("queryRoot") Map<String, Object> queryRoots,
                                             DataLoaderRegistry dataLoaderRegistry, CaasServiceConfigurationProperties caasServiceConfigurationProperties,
                                             @Qualifier(ATTRIBUTE_NAMES_TO_GQL_CONTEXT) Set<String> requestAttributeNamesToGraphqlContext) {
    return new GraphQLInvocationImpl(graphQL, renameQueryRootsWithOptionalPrefix(queryRoots), dataLoaderRegistry, caasServiceConfigurationProperties, requestAttributeNamesToGraphqlContext);
  }

  @Bean
  public ExecutionResultHandler executionResultHandler() {
    return new CaasExecutionResultHandler();
  }

  @Bean
  public QueryNormalizer queryNormalizer() {
    return new DefaultQueryNormalizer();
  }

  @Bean
  public ContextInstrumentation contextInstrumentation() {
    return new ContextInstrumentation();
  }

  @Bean
  public DataFetcherMappingInstrumentation dataFetchingInstrumentation(SpelEvaluationStrategy spelEvaluationStrategy,
                                                                       @Qualifier("filterPredicate") List<Predicate<Object>> filterPredicates,
                                                                       @Qualifier("graphQlConversionService") ConversionService conversionService,
                                                                       @Qualifier("conversionTypeMap") Map<String, Class<?>> conversionTypeMap,
                                                                       SitesService sitesService
  ) {
    return new DataFetcherMappingInstrumentation((dataFetcher, parameters) ->
            new ConvertingDataFetcher(
                    new FilteringDataFetcher(
                            new ViewBySiteFilterDataFetcher(dataFetcher, sitesService, caasServiceConfigurationProperties),
                            filterPredicates
                    ),
                    conversionService,
                    conversionTypeMap
            )
    );
  }

  @Bean
  public ExecutionTimeoutInstrumentation executionTimeoutInstrumentation() {
    if (caasGraphqlConfigurationProperties.getMaxExecutionTimeout() > 0) {
      LOG.info("caas.graphql.max-execution-timeout: {} ms", caasGraphqlConfigurationProperties.getMaxExecutionTimeout());
      return new ExecutionTimeoutInstrumentation(caasGraphqlConfigurationProperties.getMaxExecutionTimeout());
    }
    return null;
  }

  @Bean
  public MaxQueryDepthInstrumentation maxQueryDepthInstrumentation() {
    if (caasGraphqlConfigurationProperties.getMaxQueryDepth() > 0) {
      LOG.info("caas.graphql.max-query-depth: {}", caasGraphqlConfigurationProperties.getMaxQueryDepth());
      return new MaxQueryDepthInstrumentation(caasGraphqlConfigurationProperties.getMaxQueryDepth());
    }
    return null;
  }

  @Bean
  public MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation() {
    if (caasGraphqlConfigurationProperties.getMaxQueryComplexity() > 0) {
      LOG.info("caas.graphql.max-query-complexity: {}", caasGraphqlConfigurationProperties.getMaxQueryComplexity());
      return new MaxQueryComplexityInstrumentation(caasGraphqlConfigurationProperties.getMaxQueryComplexity());
    }
    return null;
  }

  @Bean
  public TypeDefinitionRegistry typeDefinitionRegistry()
          throws IOException {
    SchemaParser schemaParser = new SchemaParser();
    PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
    StringBuilder stringBuilder = new StringBuilder();
    Resource[] resources = loader.getResources("classpath*:*-schema.graphql");

    List<Resource> allResources = new ArrayList<>(Arrays.asList(resources));

    for (Resource resource : allResources) {
      LOG.info("merging GraphQL schema {}", resource.getURI());
      try (InputStreamReader in = new InputStreamReader(resource.getInputStream())) {
        stringBuilder.append(IOUtils.toString(in));
      } catch (IOException e) {
        throw new IOException("There was an error while reading the schema file " + resource.getFilename(), e);
      }
    }
    return schemaParser.parse(stringBuilder.toString());
  }

  @Bean
  public GraphQLSchema graphQLSchema(Map<String, SchemaDirectiveWiring> directiveWirings,
                                     @Qualifier("rootModelMapper") ModelMapper<Object, Object> modelMapper,
                                     List<WiringFactory> wiringFactories)
          throws IOException {
    WiringFactory wiringFactory = new ModelMappingWiringFactory(modelMapper, wiringFactories);
    RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring()
            .wiringFactory(wiringFactory);
    directiveWirings.forEach(builder::directive);
    RuntimeWiring wiring = builder.build();
    TypeDefinitionRegistry typeRegistry = typeDefinitionRegistry();
    SchemaGenerator schemaGenerator = new SchemaGenerator();
    return schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
  }

  @Bean
  public GraphQL graphQL(GraphQLSchema graphQLSchema,
                         List<Instrumentation> instrumentations) {
    return GraphQL.newGraphQL(graphQLSchema)
            .instrumentation(new ChainedInstrumentation(instrumentations))
            .build();
  }

  @Bean
  @Qualifier("conversionTypeMap")
  public Map<String, Class<?>> conversionTypeMap() {

    /* add corresponding custom scalar types from content-schema.graphql here */
    return new ImmutableMap.Builder<String, Class<?>>()
            .put("JSON", Map.class)
            .put("BigDecimal", BigDecimal.class)
            .build();

  }

  @Bean
  public GraphQLScalarType JSON() {
    return ExtendedScalars.Json;
  }

  @Bean
  public GraphQLScalarType BigDecimal() {
    return GraphQLScalarType.newScalar().name("BigDecimal").description("java.math.BigDecimal").coercing(new CoercingBigDecimal()).build();
  }

  private Map<String, Object> renameQueryRootsWithOptionalPrefix(Map<String, Object> queryRoots) {
    Map<String, Object> renamedQueryRoots = new LinkedHashMap<>(queryRoots.size());
    for (var rootEntry : queryRoots.entrySet()) {
      String name = rootEntry.getKey();
      if (name.startsWith(OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX)) {
        name = name.substring(OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX.length());
        LOG.info("adding GraphQL query root {} (renamed from {})", name, rootEntry.getKey());
      } else {
        LOG.info("adding GraphQL query root {}", name);
      }
      renamedQueryRoots.put(name, rootEntry.getValue());
    }
    return renamedQueryRoots;
  }

  @Bean
  public DataLoaderRegistry dataLoaderRegistry() {
    return new DataLoaderRegistry();
  }
}
