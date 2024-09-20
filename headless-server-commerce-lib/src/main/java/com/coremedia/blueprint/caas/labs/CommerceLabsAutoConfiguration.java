package com.coremedia.blueprint.caas.labs;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.BaseCommerceServicesAutoConfiguration;
import com.coremedia.blueprint.base.livecontext.ecommerce.common.CommerceConnectionSupplier;
import com.coremedia.blueprint.caas.labs.model.CommerceLabsFacade;
import com.coremedia.blueprint.caas.labs.model.Metadata;
import com.coremedia.blueprint.caas.labs.model.SiteResolver;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.cache.Cache;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.livecontext.ecommerce.catalog.Catalog;
import com.coremedia.livecontext.ecommerce.catalog.Category;
import com.coremedia.livecontext.ecommerce.catalog.Product;
import com.coremedia.livecontext.ecommerce.catalog.ProductVariant;
import com.coremedia.livecontext.ecommerce.common.BaseCommerceBeanType;
import com.coremedia.livecontext.ecommerce.common.CommerceBean;
import com.coremedia.livecontext.ecommerce.common.CommerceBeanType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@AutoConfiguration(after = BaseCommerceServicesAutoConfiguration.class)
public class CommerceLabsAutoConfiguration {

  private static final Set<String> SCHEMA_TYPE_NAMES = Set.of(
          CommerceBean.class.getSimpleName(),
          Catalog.class.getSimpleName(),
          Category.class.getSimpleName(),
          Product.class.getSimpleName(),
          ProductVariant.class.getSimpleName(),
          Metadata.class.getSimpleName());

  private static final Map<CommerceBeanType, String> TYPE_RESOLVE_MAP = Map.of(
          BaseCommerceBeanType.CATEGORY, "CategoryImpl",
          BaseCommerceBeanType.PRODUCT, "ProductImpl",
          BaseCommerceBeanType.SKU, "ProductVariantImpl",
          BaseCommerceBeanType.CATALOG, "CatalogImpl"
  );

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
      CommerceBeanType commerceBeanType = commerceBean.getId().getCommerceBeanType();
      return Optional.ofNullable(TYPE_RESOLVE_MAP.get(commerceBeanType));
    };
  }

  @Bean
  @Qualifier("graphqlSchemaResource")
  public Resource commerceSchemaResource() throws IOException {
    PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
    return Arrays.stream(loader.getResources("classpath*:commerce-schema.graphql"))
            .findFirst()
            .orElseThrow(() -> new IOException("GraphQl schema resource 'commerce-schema.graphql' not found."));
  }

  @Bean("query-root:catalog")
  @Qualifier("queryRoot")
  public Object commerce() {
    // A simple Object suffices because all commerce root fields are implemented via @fetch directives
    return new Object();
  }

  @Bean
  public CommerceLabsFacade commerceLabsFacade(@Qualifier("commerceConnectionSupplier") CommerceConnectionSupplier commerceConnectionSupplier,
                                               SitesService sitesService, SiteResolver siteResolver) {
    return new CommerceLabsFacade(commerceConnectionSupplier, sitesService, siteResolver);
  }

  @Bean
  public SiteResolver siteResolver(SitesService sitesService, CommerceConnectionSupplier commerceConnectionSupplier, Cache cache) {
    return new SiteResolver(sitesService, commerceConnectionSupplier, cache);
  }
}
