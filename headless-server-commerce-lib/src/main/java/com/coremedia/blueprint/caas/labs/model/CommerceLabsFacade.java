package com.coremedia.blueprint.caas.labs.model;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.CommerceConnectionSupplier;
import com.coremedia.blueprint.base.livecontext.ecommerce.id.CommerceIdBuilder;
import com.coremedia.blueprint.base.livecontext.ecommerce.id.CommerceIdFormatterHelper;
import com.coremedia.blueprint.base.livecontext.ecommerce.id.CommerceIdParserHelper;
import com.coremedia.blueprint.caas.labs.error.CommerceConnectionUnavailable;
import com.coremedia.caas.model.error.SiteIdUndefined;
import com.coremedia.cap.multisite.Site;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.livecontext.ecommerce.catalog.Catalog;
import com.coremedia.livecontext.ecommerce.catalog.CatalogAlias;
import com.coremedia.livecontext.ecommerce.catalog.CatalogId;
import com.coremedia.livecontext.ecommerce.catalog.CatalogService;
import com.coremedia.livecontext.ecommerce.catalog.Category;
import com.coremedia.livecontext.ecommerce.catalog.Product;
import com.coremedia.livecontext.ecommerce.catalog.ProductVariant;
import com.coremedia.livecontext.ecommerce.common.BaseCommerceBeanType;
import com.coremedia.livecontext.ecommerce.common.CommerceBean;
import com.coremedia.livecontext.ecommerce.common.CommerceConnection;
import com.coremedia.livecontext.ecommerce.common.CommerceException;
import com.coremedia.livecontext.ecommerce.common.CommerceId;
import com.coremedia.livecontext.ecommerce.common.CommerceIdProvider;
import com.coremedia.livecontext.ecommerce.common.StoreContext;
import com.coremedia.livecontext.ecommerce.search.SearchResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import graphql.execution.DataFetcherResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;

public class CommerceLabsFacade {
  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());
  private static final String SERVICE_TYPE = "catalog";

  private final CommerceConnectionSupplier commerceConnectionSupplier;
  private final SitesService sitesService;
  private final SiteResolver siteResolver;

  public CommerceLabsFacade(CommerceConnectionSupplier commerceConnectionSupplier, SitesService sitesService, SiteResolver siteResolver) {
    this.commerceConnectionSupplier = commerceConnectionSupplier;
    this.sitesService = sitesService;
    this.siteResolver = siteResolver;
  }

  public DataFetcherResult<Store> getStoreContext(String siteId) {
    DataFetcherResult.Builder<Store> builder = DataFetcherResult.newResult();
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    StoreContext storeContext = connection.getStoreContext();

    return builder.data(Store.from(storeContext, connection.getVendor().value())).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalogs(#siteId)")
  public DataFetcherResult<List<Catalog>> getCatalogs(String siteId) {
    DataFetcherResult.Builder<List<Catalog>> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }

    try {
      CatalogService catalogService = connection.getCatalogService();
      return builder.data(catalogService.getCatalogs(connection.getStoreContext())).build();
    } catch (CommerceException e) {
      LOG.warn("Could not retrieve catalogs for siteId {}", siteId, e);
      return builder.build();
    }
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalog(#catalogId, #siteId)")
  public DataFetcherResult<Catalog> getCatalog(String catalogId, String siteId) {
    DataFetcherResult.Builder<Catalog> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    if (catalogId == null) {
      return builder.data(getDefaultCatalog(siteId)).build();
    }

    CommerceId commerceId = CommerceIdBuilder.builder(connection.getVendor(), SERVICE_TYPE, BaseCommerceBeanType.CATALOG)
            .withExternalId(catalogId).build();
    return builder.data((Catalog) createCommerceBean(commerceId, connection)).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalogByAlias(#catalogAlias, #siteId)")
  public DataFetcherResult<Catalog> getCatalogByAlias(String catalogAlias, String siteId) {
    DataFetcherResult.Builder<Catalog> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    StoreContext storeContext = connection.getStoreContext();

    try {
      CatalogService catalogService = connection.getCatalogService();
      return builder.data(catalogService
              .getCatalog(CatalogAlias.of(catalogAlias), storeContext)
              .orElse(null)).build();
    } catch (CommerceException e) {
      LOG.warn("Could not retrieve catalog for catalogAlias {}", catalogAlias, e);
      return builder.build();
    }
  }

  @Nullable
  public Catalog getDefaultCatalog(String siteId) {
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return null;
    }
    StoreContext storeContext = connection.getStoreContext();

    try {
      CatalogService catalogService = connection.getCatalogService();
      return catalogService
              .getDefaultCatalog(storeContext)
              .orElse(null);
    } catch (CommerceException e) {
      LOG.warn("Could not retrieve default catalog", e);
      return null;
    }
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCategory(#categoryId, #siteId)")
  public DataFetcherResult<Category> getCategory(String categoryId, String siteId) {
    DataFetcherResult.Builder<Category> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    CommerceId commerceId = getCategoryId(categoryId, connection);
    CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getStoreContext());
    return builder.data((Category) bean).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCategoryByStore(#categoryId, #locale, #storeId, #catalogId)")
  public DataFetcherResult<Category> getCategoryByStore(String categoryId, String localeAsString, String storeId, String catalogId) {
    DataFetcherResult.Builder<Category> builder = DataFetcherResult.newResult();
    if (storeId == null || localeAsString == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    Locale locale = Locale.forLanguageTag(localeAsString);

    CommerceConnection connection = getCommerceConnection(storeId, locale);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    CommerceId commerceId = getCategoryId(categoryId, connection);
    CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getStoreContext());
    return builder.data((Category) bean).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCommerceBean(#commerceId, #siteId)")
  @Nullable
  public DataFetcherResult<CommerceBean> getCommerceBean(String commerceId, String siteId) {
    DataFetcherResult.Builder<CommerceBean> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    return builder.data(createCommerceBean(commerceId, siteId, CommerceBean.class)).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getProduct(#externalId, #siteId)")
  public DataFetcherResult<Product> getProduct(String externalId, String siteId) {
    DataFetcherResult.Builder<Product> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    CommerceId commerceId = getProductId(externalId, connection);
    CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getStoreContext());
    return builder.data((Product) bean).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getProductByTechId(#techId, #siteId)")
  public DataFetcherResult<Product> getProductByTechId(String techId, String siteId) {
    DataFetcherResult.Builder<Product> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    StoreContext storeContext = connection.getStoreContext();
    CommerceId productCommerceId = connection.getIdProvider().formatProductTechId(storeContext.getCatalogAlias(), techId);
    try {
      CatalogService catalogService = connection.getCatalogService();
      return builder.data(catalogService.findProductById(productCommerceId, storeContext)).build();
    } catch (CommerceException e) {
      LOG.warn("Could not retrieve product for techId {}", techId, e);
      return builder.build();
    }
  }

  @Nullable
  public SearchResult<Product> searchProducts(String searchTerm, Map<String, String> searchParams, String siteId) {
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return null;
    }
    StoreContext storeContext = connection.getStoreContext();

    try {
      CatalogService catalogService = connection.getCatalogService();
      //noinspection removal
      return catalogService.searchProducts(searchTerm, searchParams, storeContext);
    } catch (CommerceException e) {
      LOG.warn("Could not search products with searchTerm {}", searchTerm, e);
      return null;
    }
  }

  @Nullable
  public SearchResult<ProductVariant> searchProductVariants(String searchTerm, Map<String, String> searchParams, String siteId) {
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return null;
    }
    StoreContext storeContext = connection.getStoreContext();

    try {
      CatalogService catalogService = connection.getCatalogService();
      //noinspection removal
      return catalogService.searchProductVariants(searchTerm, searchParams, storeContext);
    } catch (CommerceException e) {
      LOG.warn("Could not search product variants with searchTerm {}", searchTerm, e);
      return null;
    }
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.findProductBySeoSegment(#seoSegment, #siteId)")
  public DataFetcherResult<Product> findProductBySeoSegment(String seoSegment, String siteId) {
    DataFetcherResult.Builder<Product> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    StoreContext storeContext = connection.getStoreContext();
    try {
      CatalogService catalogService = connection.getCatalogService();
      return builder.data(catalogService.findProductBySeoSegment(seoSegment, storeContext)).build();
    } catch (CommerceException e) {
      LOG.warn("Could not retrieve product for seoSegment {}", seoSegment, e);
      return builder.build();
    }
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.findCategoryBySeoSegment(#seoSegment, #siteId)")
  public DataFetcherResult<Category> findCategoryBySeoSegment(String seoSegment, String siteId) {
    DataFetcherResult.Builder<Category> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    StoreContext storeContext = connection.getStoreContext();

    try {
      CatalogService catalogService = connection.getCatalogService();
      return builder.data(catalogService.findCategoryBySeoSegment(seoSegment, storeContext)).build();
    } catch (CommerceException e) {
      LOG.warn("Could not retrieve category by seoSegment {}", seoSegment, e);
      return builder.build();
    }
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getProductVariant(#externalId, #siteId)")
  public DataFetcherResult<ProductVariant> getProductVariant(String productVariantId, String siteId) {
    DataFetcherResult.Builder<ProductVariant> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }
    CommerceId commerceId = getProductVariantId(productVariantId, connection);
    CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getStoreContext());
    return builder.data((ProductVariant) bean).build();
  }


  @Nullable
  private CommerceBean createCommerceBean(CommerceId commerceId, CommerceConnection commerceConnection) {
    StoreContext storeContext = commerceConnection.getStoreContext();
    return commerceConnection.getCommerceBeanFactory().createBeanFor(commerceId, storeContext);
  }

  @Nullable
  private <T extends CommerceBean> T createCommerceBean(String id, String siteId, Class<T> expectedType) {
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return null;
    }
    CommerceBean bean = createCommerceBean(id, connection);
    if (bean == null || !expectedType.isAssignableFrom(bean.getClass())) {
      return null;
    }
    return (T) bean;
  }

  @Nullable
  private CommerceBean createCommerceBean(String id, CommerceConnection commerceConnection) {
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(id);
    if (commerceIdOptional.isEmpty()) {
      LOG.debug("unknown id: '{}'", id);
      return null;
    }
    return createCommerceBean(commerceIdOptional.get(), commerceConnection);
  }

  private CommerceConnection getCommerceConnection(String storeId, Locale locale) {
    return siteResolver.findSiteFor(storeId, locale)
            .map(site -> getCommerceConnection(site.getId()))
            .orElse(null);
  }

  @Nullable
  private CommerceConnection getCommerceConnection(String siteId) {
    try {
      Site site = sitesService.getSite(siteId);
      if (site == null) {
        LOG.info("Cannot find site for siteId {}.", siteId);
        return null;
      }
      CommerceConnection connection = commerceConnectionSupplier.findConnection(site)
              .orElse(null);

      if (connection == null) {
        LOG.warn("Cannot find commerce connection for siteId {}", siteId);
        return null;
      }
      return connection;
    } catch (CommerceException e) {
      LOG.warn("Cannot find commerce connection for siteId {}", siteId, e);
      return null;
    }
  }

  /**
   * Ensures that the id is in the long format, which is required by subsequent calls:
   * <p>
   * Example: <code>vendor:///summer_catalog/product/foo-1</code> or <code>vendor:///catalog/product/foo-1</code>
   *
   * @param productId  the external id
   * @param connection the commerce connection to be used
   * @return id in the long format
   */
  @NonNull
  private static CommerceId getProductId(String productId, CommerceConnection connection) {
    CommerceIdProvider idProvider = connection.getIdProvider();
    CatalogAlias catalogAlias = connection.getStoreContext().getCatalogAlias();
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(productId);
    return commerceIdOptional.orElseGet(() -> idProvider.formatProductId(catalogAlias, productId));
  }

  @NonNull
  private static CommerceId getProductVariantId(String productVariantId, CommerceConnection connection) {
    CommerceIdProvider idProvider = connection.getIdProvider();
    CatalogAlias catalogAlias = connection.getStoreContext().getCatalogAlias();
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(productVariantId);
    return commerceIdOptional.orElseGet(() -> idProvider.formatProductVariantId(catalogAlias, productVariantId));
  }

  /**
   * Ensures that the id is in the long format, which is required by subsequent calls:
   * <p>
   * Example: <code>vendor:///summer_catalog/category/men</code> or <code>vendor:///catalog/category/men</code>
   *
   * @param categoryId the external id
   * @param connection the commerce connection to be used
   * @return id in the long format
   */

  @NonNull
  private CommerceId getCategoryId(String categoryId, CommerceConnection connection) {
    CommerceIdProvider idProvider = connection.getIdProvider();
    CatalogAlias catalogAlias = connection.getStoreContext().getCatalogAlias();
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(categoryId);
    return commerceIdOptional.orElseGet(() -> idProvider.formatCategoryId(catalogAlias, categoryId));
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCommerceId(#this)")
  @Nullable
  public static String getCommerceId(CommerceBean commerceBean) {
    return CommerceIdFormatterHelper.format(commerceBean.getId());
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalogId(#this)")
  @Nullable
  public static String getCatalogId(CommerceBean commerceBean) {
    return commerceBean.getContext().getCatalogId().map(CatalogId::value).orElse(null);
  }

}
