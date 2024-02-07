package com.coremedia.blueprint.headlessserver;

import com.coremedia.caas.config.CaasGraphqlConfigurationProperties;
import com.coremedia.caas.filter.ValidityDateFilterPredicate;
import com.coremedia.caas.headless_server.plugin_support.extensionpoints.CaasWiringFactory;
import com.coremedia.caas.headless_server.plugin_support.extensionpoints.CopyToContextParameter;
import com.coremedia.caas.headless_server.plugin_support.extensionpoints.FilterPredicate;
import com.coremedia.caas.link.GraphQLLink;
import com.coremedia.caas.model.mapper.CompositeModelMapper;
import com.coremedia.caas.model.mapper.FilteringModelMapper;
import com.coremedia.caas.model.mapper.ModelMapper;
import com.coremedia.caas.model.mapper.ModelMappingPropertyAccessor;
import com.coremedia.caas.model.mapper.ModelMappingWiringFactory;
import com.coremedia.caas.schema.SchemaParser;
import com.coremedia.caas.service.cache.CacheInstances;
import com.coremedia.caas.service.cache.Weighted;
import com.coremedia.caas.spel.SpelDirectiveWiring;
import com.coremedia.caas.spel.SpelEvaluationStrategy;
import com.coremedia.caas.spel.SpelFunctions;
import com.coremedia.caas.web.CaasServiceConfigurationProperties;
import com.coremedia.caas.web.CaasWebConfig;
import com.coremedia.caas.web.GraphQLRestMappingConfig;
import com.coremedia.caas.web.GraphiqlConfigurationProperties;
import com.coremedia.caas.web.controller.ViewController;
import com.coremedia.caas.web.controller.graphql.GraphQLController;
import com.coremedia.caas.web.controller.graphql.GraphQLErrorController;
import com.coremedia.caas.web.filter.HSTSResponseHeaderFilter;
import com.coremedia.caas.web.metadata.PropertyMappingConfig;
import com.coremedia.caas.web.monitoring.CaasMetricsConfig;
import com.coremedia.caas.web.persistedqueries.DefaultQueryNormalizer;
import com.coremedia.caas.web.persistedqueries.QueryNormalizer;
import com.coremedia.caas.web.swagger.SwaggerConfig;
import com.coremedia.caas.web.wiring.GraphQLInvocationImpl;
import com.coremedia.caas.wiring.CapStructPropertyAccessor;
import com.coremedia.caas.wiring.CompositeTypeNameResolver;
import com.coremedia.caas.wiring.CompositeTypeNameResolverProvider;
import com.coremedia.caas.wiring.ContentRepositoryWiringFactory;
import com.coremedia.caas.wiring.ExecutionTimeoutInstrumentation;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolverWiringFactory;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.undoc.common.spring.CapRepositoriesConfiguration;
import com.coremedia.cms.common.plugins.plugin_framework_autoconfiguration.PluginManagerAutoConfiguration;
import com.coremedia.function.PostProcessor;
import com.coremedia.link.CompositeLinkComposer;
import com.coremedia.link.LinkComposer;
import com.coremedia.springframework.customizer.CustomizerConfiguration;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
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
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_CAAS_FILTER_PREDICATE;
import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_CAAS_WIRING_FACTORY;
import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_PLUGIN_FILTER_PREDICATE;
import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_PLUGIN_WIRING_FACTORIES;
import static com.coremedia.caas.web.CaasWebConfig.ATTRIBUTE_NAMES_TO_GQL_CONTEXT;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Collections.emptyList;

@AutoConfiguration(after = PluginManagerAutoConfiguration.class)
@EnableConfigurationProperties({
        CaasServiceConfigurationProperties.class,
        CaasGraphqlConfigurationProperties.class,
        GraphiqlConfigurationProperties.class,
})
@EnableWebMvc
@Import({
        CustomizerConfiguration.class,
        CapRepositoriesConfiguration.class,
        // copy of CaasWebAutoConfiguration imports except MediaConfig
        CaasWebConfig.class,
        GraphQLRestMappingConfig.class,
        SwaggerConfig.class,
        PropertyMappingConfig.class,
        CaasMetricsConfig.class,
        GraphQLController.class,
        GraphQLErrorController.class,
        ViewController.class,
})
public class CommerceCaasAutoConfiguration implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());
  private static final String OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX = "query-root:";
  private static final int TWENTY_FOUR_HOURS = 24 * 60 * 60;
  private static final int CORS_RESPONSE_MAX_AGE = TWENTY_FOUR_HOURS;

  private final CaasServiceConfigurationProperties caasServiceConfigurationProperties;
  private final CaasGraphqlConfigurationProperties caasGraphqlConfigurationProperties;
  private final GraphiqlConfigurationProperties graphiqlConfigurationProperties;

  public CommerceCaasAutoConfiguration(CaasServiceConfigurationProperties caasServiceConfigurationProperties,
                                       CaasGraphqlConfigurationProperties caasGraphqlConfigurationProperties,
                                       GraphiqlConfigurationProperties graphiqlConfigurationProperties) {
    this.caasServiceConfigurationProperties = caasServiceConfigurationProperties;
    this.caasGraphqlConfigurationProperties = caasGraphqlConfigurationProperties;
    this.graphiqlConfigurationProperties = graphiqlConfigurationProperties;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void configurePathMatch(PathMatchConfigurer matcher) {
    matcher.setUseSuffixPatternMatch(false);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowCredentials(true)
            .maxAge(CORS_RESPONSE_MAX_AGE);
  }

  @Override
  public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
    List<String> resources = new ArrayList<>(Arrays.asList("/static/**", "/docs/**"));
    List<String> resourceLocations = new ArrayList<>();
    if (graphiqlConfigurationProperties.isEnabled()) {
      resources.add("/graphiql/static/**");
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

  @Bean
  public Filter hstsResponseHeaderFilter(CaasServiceConfigurationProperties caasServiceConfigurationProperties) {
    return new HSTSResponseHeaderFilter(caasServiceConfigurationProperties);
  }

  @Bean("cacheManager")
  public CacheManager cacheManager() {
    List<Cache> list = caasServiceConfigurationProperties.getCacheSpecs().entrySet().stream()
            .map(entry -> createCache(entry.getKey(), entry.getValue()))
            .collect(Collectors.toUnmodifiableList());
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(list);
    return cacheManager;
  }

  @NonNull
  private static CaffeineCache createCache(String cacheName, String cacheSpec) {
    com.github.benmanes.caffeine.cache.Cache<Object, Object> cache = Caffeine.from(cacheSpec)
            .weigher((key, value) -> {
              if (value instanceof Weighted) {
                return ((Weighted) value).getWeight();
              }
              return String.valueOf(value).length();
            })
            .build();
    return new CaffeineCache(cacheName, cache);
  }

  @Bean
  public LinkComposer<Object, GraphQLLink> graphQlLinkComposer(List<LinkComposer<?, ? extends GraphQLLink>> linkComposers) {
    return new CompositeLinkComposer<>(linkComposers, emptyList());
  }

  @Bean
  public TypeNameResolver<Object> compositeTypeNameResolver(List<TypeNameResolver<?>> typeNameResolvers, List<PostProcessor<?, ? extends String>> postProcessors) {
    return new CompositeTypeNameResolver<>(typeNameResolvers, postProcessors);
  }

  @Bean
  public ProvidesTypeNameResolver compositeProvidesTypeNameResolver(List<ProvidesTypeNameResolver> providesTypeNameResolvers) {
    return new CompositeTypeNameResolverProvider(providesTypeNameResolvers);
  }

  @Bean
  @Qualifier(QUALIFIER_CAAS_WIRING_FACTORY)
  public TypeNameResolverWiringFactory typeNameResolverWiringFactory(
          @Qualifier("compositeProvidesTypeNameResolver") ProvidesTypeNameResolver providesTypeNameResolver,
          @Qualifier("compositeTypeNameResolver") TypeNameResolver<Object> typeNameResolver) {
    return new TypeNameResolverWiringFactory(providesTypeNameResolver, typeNameResolver);
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
  public List<PropertyAccessor> propertyAccessors(List<PropertyAccessor> propertyAccessors, @Qualifier("rootModelMapper") FilteringModelMapper modelMapper) {
    return propertyAccessors.stream()
            .map(propertyAccessor -> new ModelMappingPropertyAccessor(propertyAccessor, modelMapper))
            .collect(Collectors.toList());
  }

  @Bean
  public SpelEvaluationStrategy spelEvaluationStrategy(
          BeanFactory beanFactory,
          @Qualifier("propertyAccessors") List<PropertyAccessor> propertyAccessors,
          @Qualifier("pluginSchemaAdapterBeansResolver") BeanResolver beanResolver,
          @Qualifier("graphQlConversionService") ConversionService conversionService) {
    return new SpelEvaluationStrategy(beanFactory, propertyAccessors, beanResolver, conversionService);
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
  @Qualifier(QUALIFIER_CAAS_WIRING_FACTORY)
  public ContentRepositoryWiringFactory contentRepositoryWiringFactory(Map<String, GraphQLScalarType> builtinScalars,
                                                                       ContentRepository repository) {
    return new ContentRepositoryWiringFactory(repository, builtinScalars);
  }

  @Bean
  @Qualifier(QUALIFIER_CAAS_FILTER_PREDICATE)
  public FilterPredicate<Object> validityDateFilterPredicate() {
    return new ValidityDateFilterPredicate();
  }

  @Bean
  public List<FilterPredicate<Object>> caasFilterPredicates(
          @Qualifier(QUALIFIER_CAAS_FILTER_PREDICATE) List<FilterPredicate<Object>> predicates,
          @Qualifier(QUALIFIER_PLUGIN_FILTER_PREDICATE) List<FilterPredicate<Object>> pluginFilterPredicates
  ) {
    return Stream.concat(predicates.stream(), pluginFilterPredicates.stream()).collect(Collectors.toList());
  }

  @Bean
  public FilteringModelMapper rootModelMapper(List<ModelMapper<?, ?>> modelMappers, @Qualifier("caasFilterPredicates") List<FilterPredicate<Object>> caasFilterPredicates) {
    return new FilteringModelMapper(new CompositeModelMapper<>(modelMappers), caasFilterPredicates);
  }

  @Bean
  public GraphQLInvocation graphQLInvocation(GraphQL graphQL,
                                             @Qualifier("queryRoot") Map<String, Object> queryRoots,
                                             DataLoaderRegistry dataLoaderRegistry,
                                             CaasServiceConfigurationProperties caasServiceConfigurationProperties,
                                             @Qualifier(ATTRIBUTE_NAMES_TO_GQL_CONTEXT) Set<String> requestAttributeNamesToGraphqlContext,
                                             @Qualifier("copyToContextParameterList") List<CopyToContextParameter<Object, Object>> copyToContextParameterList
  ) {
    return new GraphQLInvocationImpl(
            graphQL,
            renameQueryRootsWithOptionalPrefix(queryRoots),
            dataLoaderRegistry,
            caasServiceConfigurationProperties,
            requestAttributeNamesToGraphqlContext,
            copyToContextParameterList
    );
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
  @ConditionalOnProperty(prefix = "caas.graphql", name = "max-execution-timeout")
  public ExecutionTimeoutInstrumentation executionTimeoutInstrumentation() {
    long maxExecutionTimeout = caasGraphqlConfigurationProperties.getMaxExecutionTimeout().toMillis();
    LOG.info("caas.graphql.max-execution-timeout: {} ms", maxExecutionTimeout);
    return new ExecutionTimeoutInstrumentation(maxExecutionTimeout);
  }

  @Bean
  @ConditionalOnExpression("#{T(Integer).valueOf(${caas.graphql.max-query-depth:30}) > 0}")
  public MaxQueryDepthInstrumentation maxQueryDepthInstrumentation() {
    int maxQueryDepth = caasGraphqlConfigurationProperties.getMaxQueryDepth();
    LOG.info("caas.graphql.max-query-depth: {}", maxQueryDepth);
    return new MaxQueryDepthInstrumentation(maxQueryDepth);
  }

  @Bean
  @ConditionalOnExpression("#{T(Integer).valueOf(${caas.graphql.max-query-complexity:0}) > 0}")
  public MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation() {
    int maxQueryComplexity = caasGraphqlConfigurationProperties.getMaxQueryComplexity();
    LOG.info("caas.graphql.max-query-complexity: {}", maxQueryComplexity);
    return new MaxQueryComplexityInstrumentation(maxQueryComplexity);
  }

  @Bean
  public TypeDefinitionRegistry typeDefinitionRegistry(List<Resource> pluginGraphqlSchemaResources)
          throws IOException {
    SchemaParser schemaParser = new SchemaParser();
    PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
    StringBuilder stringBuilder = new StringBuilder();
    Resource[] resources = loader.getResources("classpath*:*-schema.graphql");

    List<Resource> allResources = new ArrayList<>(Arrays.asList(resources));

    List<String> additionalSchemaLocations = caasServiceConfigurationProperties.getAdditionSchemaLocations();
    if (additionalSchemaLocations != null) {
      for (String additionalLocation : additionalSchemaLocations) {
        Resource[] additionalResources = loader.getResources(additionalLocation);
        allResources.addAll(Arrays.asList(additionalResources));
      }
    }

    allResources.addAll(pluginGraphqlSchemaResources);

    for (Resource resource : allResources) {
      LOG.info("Merging GraphQL schema {}", resource.getURI());
      try (InputStreamReader in = new InputStreamReader(resource.getInputStream())) {
        stringBuilder.append(IOUtils.toString(in));
      } catch (IOException e) {
        throw new IOException("There was an error while reading the schema file " + resource.getFilename(), e);
      }
    }
    return schemaParser.parse(stringBuilder.toString());
  }

  @Bean
  @Qualifier("caasWiringFactories")
  public List<WiringFactory> caasWiringFactories(
          @Qualifier(QUALIFIER_CAAS_WIRING_FACTORY) List<WiringFactory> wiringFactories,
          @Qualifier(QUALIFIER_PLUGIN_WIRING_FACTORIES) List<CaasWiringFactory> pluginWiringFactories
  ) {
    return Stream.concat(wiringFactories.stream(), pluginWiringFactories.stream()).collect(Collectors.toList());
  }

  @Bean
  public GraphQLSchema graphQLSchema(Map<String, SchemaDirectiveWiring> directiveWirings,
                                     TypeDefinitionRegistry typeRegistry,
                                     @Qualifier("rootModelMapper") FilteringModelMapper modelMapper,
                                     @Qualifier("caasWiringFactories") List<WiringFactory> wiringFactories) {
    WiringFactory wiringFactory = new ModelMappingWiringFactory(modelMapper, wiringFactories);
    RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring()
            .wiringFactory(wiringFactory);
    directiveWirings.forEach(builder::directive);
    RuntimeWiring wiring = builder.build();
    SchemaGenerator schemaGenerator = new SchemaGenerator();
    return schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
  }

  @Bean
  public PreparsedDocumentProvider preparsedDocumentProvider(CacheManager cacheManager) {
    return new PreparsedDocumentProvider() {
      final Cache cache = cacheManager.getCache(CacheInstances.PREPARSED_DOCUMENTS);
      final Function<ExecutionInput, PreparsedDocumentEntry> computeFunction = executionInput -> new PreparsedDocumentEntry(
              graphql.parser.Parser.parse(executionInput.getQuery())
      );

      @Override
      public PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> computeFunction) {
        return cache.get(executionInput.getQuery(), () -> computeFunction.apply(executionInput));
      }
    };
  }

  @Bean
  public GraphQL graphQL(GraphQLSchema graphQLSchema,
                         PreparsedDocumentProvider preparsedDocumentProvider,
                         List<Instrumentation> instrumentations) {
    return GraphQL.newGraphQL(graphQLSchema)
            .instrumentation(new ChainedInstrumentation(instrumentations))
            .preparsedDocumentProvider(preparsedDocumentProvider)
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
    return ExtendedScalars.GraphQLBigDecimal;
  }

  private Map<String, Object> renameQueryRootsWithOptionalPrefix(Map<String, Object> queryRoots) {
    Map<String, Object> renamedQueryRoots = new LinkedHashMap<>(queryRoots.size());
    for (var rootEntry : queryRoots.entrySet()) {
      String name = rootEntry.getKey();
      if (name.startsWith(OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX)) {
        name = name.substring(OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX.length());
        LOG.info("Adding GraphQL query root '{}'. (renamed from {})", name, rootEntry.getKey());
      } else {
        LOG.info("Adding GraphQL query root '{}'.", name);
      }
      renamedQueryRoots.put(name, rootEntry.getValue());
    }
    return renamedQueryRoots;
  }

  @Bean
  public DataLoaderRegistry dataLoaderRegistry(Map<String, DataLoader<String, Try<String>>> dataLoaders) {
    DataLoaderRegistry registry = new DataLoaderRegistry();
    dataLoaders.forEach(registry::register);
    return registry;
  }

}
