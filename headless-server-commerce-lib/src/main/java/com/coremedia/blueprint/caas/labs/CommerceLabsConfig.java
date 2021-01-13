package com.coremedia.blueprint.caas.labs;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.BaseCommerceServicesAutoConfiguration;
import com.coremedia.blueprint.base.livecontext.ecommerce.common.CommerceConnectionInitializer;
import com.coremedia.blueprint.caas.labs.model.CommerceLabsFacade;
import com.coremedia.blueprint.caas.labs.model.Metadata;
import com.coremedia.blueprint.caas.labs.model.SiteResolver;
import com.coremedia.blueprint.caas.labs.wiring.CommerceInstrumentation;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.cache.Cache;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.livecontext.ecommerce.catalog.Catalog;
import com.coremedia.livecontext.ecommerce.catalog.Category;
import com.coremedia.livecontext.ecommerce.catalog.Product;
import com.coremedia.livecontext.ecommerce.catalog.ProductVariant;
import com.coremedia.livecontext.ecommerce.common.CommerceBean;
import com.google.common.base.CaseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration(proxyBeanMethods = false)
@Import({
        BaseCommerceServicesAutoConfiguration.class
})
public class CommerceLabsConfig {

  private static final Set<String> SCHEMA_TYPE_NAMES = Stream.of(
          CommerceBean.class.getSimpleName(),
          Catalog.class.getSimpleName(),
          Category.class.getSimpleName(),
          Product.class.getSimpleName(),
          ProductVariant.class.getSimpleName(),
          Metadata.class.getSimpleName())
          .collect(Collectors.toSet());

  @Bean
  public ProvidesTypeNameResolver providesCommerceBeanTypeNameResolver() {
    return typeName ->
            SCHEMA_TYPE_NAMES.contains(typeName)
                    ? Optional.of(true)
                    : Optional.empty();
  }

  @Bean
  public TypeNameResolver<CommerceBean> commerceBeanTypeNameResolver() {
    return commerceBean -> {
      String type = commerceBean.getId().getCommerceBeanType().type();
      String graphQlTypeName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type) + "Impl";
      return Optional.of(graphQlTypeName);
    };
  }

  @Bean("query-root:catalog")
  @Qualifier("queryRoot")
  public Object commerce(CommerceLabsFacade commerceLabsFacade) {
    // A simple Object suffices because all commerce root fields are implemented via @fetch directives
    return new Object();
  }

  @Bean
  public CommerceLabsFacade commerceLabsFacade(@Qualifier("commerceConnectionInitializer") CommerceConnectionInitializer commerceConnectionInitializer,
                                               SitesService sitesService, SiteResolver siteResolver) {
    return new CommerceLabsFacade(commerceConnectionInitializer, sitesService, siteResolver);
  }

  @Bean
  public SiteResolver siteResolver(SitesService sitesService, CommerceConnectionInitializer commerceConnectionInitializer, Cache cache){
    return new SiteResolver(sitesService, commerceConnectionInitializer, cache);
  }

  @Bean
  public CommerceInstrumentation commerceInstrumentation() {
    return new CommerceInstrumentation();
  }
}
