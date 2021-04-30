package com.coremedia.blueprint.headlessserver;

import com.coremedia.caas.config.CaasGraphqlConfigurationProperties;
import com.coremedia.caas.model.mapper.CompositeModelMapper;
import com.coremedia.caas.model.mapper.FilteringModelMapper;
import com.coremedia.caas.model.mapper.ModelMapper;
import com.coremedia.caas.model.mapper.ModelMappingPropertyAccessor;
import com.coremedia.caas.model.mapper.ModelMappingWiringFactory;
import com.coremedia.caas.schema.SchemaParser;
import com.coremedia.caas.service.cache.Weighted;
import com.coremedia.caas.spel.SpelDirectiveWiring;
import com.coremedia.caas.spel.SpelEvaluationStrategy;
import com.coremedia.caas.spel.SpelFunctions;
import com.coremedia.caas.web.CaasServiceConfigurationProperties;
import com.coremedia.caas.web.GraphiqlConfigurationProperties;
import com.coremedia.caas.web.persistedqueries.DefaultQueryNormalizer;
import com.coremedia.caas.web.persistedqueries.QueryNormalizer;
import com.coremedia.caas.web.wiring.GraphQLInvocationImpl;
import com.coremedia.caas.wiring.CapStructPropertyAccessor;
import com.coremedia.caas.wiring.CompositeTypeNameResolver;
import com.coremedia.caas.wiring.CompositeTypeNameResolverProvider;
import com.coremedia.caas.wiring.ContentRepositoryWiringFactory;
import com.coremedia.caas.wiring.ContextInstrumentation;
import com.coremedia.caas.wiring.ExecutionTimeoutInstrumentation;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.RemoteLinkWiringFactory;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolverWiringFactory;
import com.coremedia.cap.content.ContentRepository;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.coremedia.caas.web.CaasWebConfig.ATTRIBUTE_NAMES_TO_GQL_CONTEXT;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Collections.emptyMap;

@Configuration
@EnableConfigurationProperties({
        CaasServiceConfigurationProperties.class,
        CaasGraphqlConfigurationProperties.class,
        GraphiqlConfigurationProperties.class,
})
@EnableWebMvc
@ComponentScan({
        "com.coremedia.caas.web",
        "com.coremedia.cap.undoc.common.spring"
})
@ImportResource({
        "classpath:/com/coremedia/blueprint/base/settings/impl/bpbase-settings-services.xml"
})
@Import({
        CustomizerConfiguration.class
})
public class CaasConfig implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());
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
   * Example to plugin a filter predicate.
   *
   * @return the predicate
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
  public GraphQLInvocation graphQLInvocation(GraphQL graphQL,
                                             DataLoaderRegistry dataLoaderRegistry, CaasServiceConfigurationProperties caasServiceConfigurationProperties,
                                             @Qualifier(ATTRIBUTE_NAMES_TO_GQL_CONTEXT) Set<String> requestAttributeNamesToGraphqlContext) {
    return new GraphQLInvocationImpl(graphQL, emptyMap(), dataLoaderRegistry, caasServiceConfigurationProperties, requestAttributeNamesToGraphqlContext);
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
    return ExtendedScalars.GraphQLBigDecimal;
  }

  @Bean
  public DataLoaderRegistry dataLoaderRegistry() {
    return new DataLoaderRegistry();
  }
}
