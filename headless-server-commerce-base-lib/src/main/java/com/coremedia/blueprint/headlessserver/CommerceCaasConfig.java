package com.coremedia.blueprint.headlessserver;

import com.coremedia.caas.config.CaasGraphqlConfigurationProperties;
import com.coremedia.caas.filter.ValidityDateFilterPredicate;
import com.coremedia.caas.headless_server.plugin_support.PluginSupport;
import com.coremedia.caas.headless_server.plugin_support.extensionpoints.CaasWiringFactory;
import com.coremedia.caas.headless_server.plugin_support.extensionpoints.FilterPredicate;
import com.coremedia.caas.link.GraphQLLink;
import com.coremedia.caas.model.mapper.CompositeModelMapper;
import com.coremedia.caas.model.mapper.FilteringModelMapper;
import com.coremedia.caas.model.mapper.ModelMapper;
import com.coremedia.caas.model.mapper.ModelMappingPropertyAccessor;
import com.coremedia.caas.service.cache.Weighted;
import com.coremedia.caas.spel.SpelDirectiveWiring;
import com.coremedia.caas.spel.SpelEvaluationStrategy;
import com.coremedia.caas.spel.SpelFunctions;
import com.coremedia.caas.web.CaasServiceConfigurationProperties;
import com.coremedia.caas.web.CaasWebConfig;
import com.coremedia.caas.web.controller.graphql.GraphQLErrorController;
import com.coremedia.caas.web.springgraphql.GraphQlConfiguration;
import com.coremedia.caas.web.view.ViewBySiteFilterDataFetcher;
import com.coremedia.caas.wiring.CompositeTypeNameResolver;
import com.coremedia.caas.wiring.CompositeTypeNameResolverProvider;
import com.coremedia.caas.wiring.ContentRepositoryWiringFactory;
import com.coremedia.caas.wiring.ConvertingDataFetcher;
import com.coremedia.caas.wiring.DataFetcherMappingInstrumentation;
import com.coremedia.caas.wiring.ExecutionTimeoutInstrumentation;
import com.coremedia.caas.wiring.FilteringDataFetcher;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolverWiringFactory;
import com.coremedia.cap.common.CapConnection;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.function.PostProcessor;
import com.coremedia.link.CompositeLinkComposer;
import com.coremedia.link.LinkComposer;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.WiringFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_CAAS_FILTER_PREDICATE;
import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_CAAS_WIRING_FACTORY;
import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_PLUGIN_FILTER_PREDICATE;
import static com.coremedia.caas.headless_server.plugin_support.PluginSupport.QUALIFIER_PLUGIN_WIRING_FACTORIES;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Collections.emptyList;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        CaasServiceConfigurationProperties.class,
        CaasGraphqlConfigurationProperties.class,
})
@EnableWebMvc
@Import({
        GraphQlConfiguration.class,
        CaasWebConfig.class,
        GraphQLErrorController.class,
})

/**
 * Copy and adapt the configuration from the CaasConfig class.
 */
public class CommerceCaasConfig implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());
  private final CaasServiceConfigurationProperties caasServiceConfigurationProperties;
  private final CaasGraphqlConfigurationProperties caasGraphqlConfigurationProperties;

  public CommerceCaasConfig(CaasServiceConfigurationProperties caasServiceConfigurationProperties, CaasGraphqlConfigurationProperties caasGraphqlConfigurationProperties) {
    this.caasServiceConfigurationProperties = caasServiceConfigurationProperties;
    this.caasGraphqlConfigurationProperties = caasGraphqlConfigurationProperties;
  }

  @Bean
  @Qualifier("graphqlSchemaResource")
  public Resource commerceBaseSchemaResource() throws IOException {
    PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
    return Arrays.stream(loader.getResources("classpath*:commerce-base-schema.graphql"))
            .findFirst()
            .orElseThrow(() -> new IOException("GraphQl schema resource 'commerce-base-schema.graphql' not found."));
  }

  @Override
  @SuppressWarnings("deprecation")
  public void configurePathMatch(PathMatchConfigurer matcher) {
    matcher.setUseTrailingSlashMatch(true);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    List<String> resources = new ArrayList<>(Arrays.asList("/static/**", "/docs/**"));
    List<String> resourceLocations = new ArrayList<>();
    if (caasServiceConfigurationProperties.isPreview()) {
      resourceLocations.add("classpath:/static/");
      resourceLocations.add("classpath:/META-INF/resources/");
    }
    registry.addResourceHandler(resources.toArray(new String[0]))
            .addResourceLocations(resourceLocations.toArray(new String[0]));
  }

  @Bean
  @ConditionalOnProperty("caas.log-requests")
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
  public CacheManager cacheManager() {
    List<org.springframework.cache.Cache> list = caasServiceConfigurationProperties.getCacheSpecs().entrySet().stream()
            .map(entry -> createCache(entry.getKey(), entry.getValue()))
            .collect(Collectors.toUnmodifiableList());
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(list);
    return cacheManager;
  }

  @NonNull
  private CaffeineCache createCache(String cacheName, String cacheSpec) {
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
  @Qualifier("propertyAccessors")
  public List<PropertyAccessor> propertyAccessors(List<PropertyAccessor> propertyAccessors, @Qualifier("rootModelMapper") FilteringModelMapper modelMapper) {
    return propertyAccessors.stream()
            .map(propertyAccessor -> new ModelMappingPropertyAccessor(propertyAccessor, modelMapper))
            .collect(Collectors.toList());
  }

  @Bean
  public DataLoaderRegistry dataLoaderRegistry() {
    return new DataLoaderRegistry();
  }

  @Bean
  public SpelEvaluationStrategy spelEvaluationStrategy(
          BeanFactory beanFactory,
          @Qualifier("propertyAccessors") List<PropertyAccessor> propertyAccessors,
          @Qualifier(PluginSupport.QUALIFIER_PLUGIN_SCHEMA_ADAPTER_RESOLVER) BeanResolver beanResolver,
          @Qualifier("graphQlConversionService") ConversionService conversionService) {
    return new SpelEvaluationStrategy(beanFactory, propertyAccessors, beanResolver, conversionService);
  }

  @Bean
  @Qualifier("globalSpelVariables")
  public Method first() throws NoSuchMethodException {
    return SpelFunctions.class.getDeclaredMethod("first", List.class);
  }

  // the bean name is used as directive name
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
  public DataFetcherMappingInstrumentation dataFetchingInstrumentation(@Qualifier("caasFilterPredicates") List<FilterPredicate<Object>> caasFilterPredicates,
                                                                       @Qualifier("graphQlConversionService") ConversionService conversionService,
                                                                       @Qualifier("conversionTypeMap") Map<String, Class<?>> conversionTypeMap,
                                                                       SitesService sitesService
  ) {
    return new DataFetcherMappingInstrumentation((dataFetcher, parameters) ->
            new ConvertingDataFetcher(
                    new FilteringDataFetcher(
                            new ViewBySiteFilterDataFetcher(dataFetcher, sitesService, caasServiceConfigurationProperties),
                            caasFilterPredicates
                    ),
                    conversionService,
                    conversionTypeMap
            )
    );
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
  @Qualifier("caasWiringFactories")
  public List<WiringFactory> caasWiringFactories(
          @Qualifier(QUALIFIER_CAAS_WIRING_FACTORY) List<WiringFactory> wiringFactories,
          @Qualifier(QUALIFIER_PLUGIN_WIRING_FACTORIES) List<CaasWiringFactory> pluginWiringFactories
  ) {
    return Stream.concat(wiringFactories.stream(), pluginWiringFactories.stream()).collect(Collectors.toList());
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
  public PreparsedDocumentProvider preparsedDocumentProvider() {
    return NoOpPreparsedDocumentProvider.INSTANCE;
  }

  @Bean
  @ConditionalOnEnabledHealthIndicator("uapiConnectionReadiness")
  @ConditionalOnMissingBean(name = "uapiConnectionReadinessHealthIndicator")
  public UapiConnectionReadinessHealthIndicator uapiConnectionReadinessHealthIndicator (CapConnection connection) {
    return new UapiConnectionReadinessHealthIndicator(connection);
  }
}
