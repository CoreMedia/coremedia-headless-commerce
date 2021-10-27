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
import com.coremedia.livecontext.ecommerce.search.OrderBy;
import com.coremedia.livecontext.ecommerce.search.SearchQuery;
import com.coremedia.livecontext.ecommerce.search.SearchQueryBuilder;
import com.coremedia.livecontext.ecommerce.search.SearchQueryFacet;
import com.coremedia.livecontext.ecommerce.search.SearchResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import graphql.execution.DataFetcherResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  public <T> DataFetcherResult<T> fetchData(String siteId, Function<CommerceConnection, T> function) {
    DataFetcherResult.Builder<T> builder = DataFetcherResult.newResult();
    if (siteId == null) {
      return builder.error(SiteIdUndefined.getInstance()).build();
    }
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return builder.error(CommerceConnectionUnavailable.getInstance()).build();
    }

    try {
      return builder.data(function.apply(connection)).build();
    } catch (Exception e) {
      LOG.warn("Could not apply function", e);
      return null;
    }
  }

  @SuppressWarnings("unused")
  @Deprecated
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalogs(#siteId)")
  public DataFetcherResult<List<Catalog>> getCatalogs(String siteId) {
    return fetchData(siteId, connection -> {
      CatalogService catalogService = connection.getCatalogService();
      return catalogService.getCatalogs(connection.getInitialStoreContext());
    });
  }

  @SuppressWarnings("unused")
  @Deprecated
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalog(#catalogId, #siteId)")
  public DataFetcherResult<Catalog> getCatalog(String catalogId, String siteId) {
    return fetchData(siteId, connection -> {
      CommerceId commerceId = CommerceIdBuilder.builder(connection.getVendor(), SERVICE_TYPE, BaseCommerceBeanType.CATALOG)
              .withExternalId(catalogId).build();
      return (Catalog) createCommerceBean(commerceId, connection);
    });


  }

  @SuppressWarnings("unused")
  @Deprecated
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCatalogByAlias(#catalogAlias, #siteId)")
  public DataFetcherResult<Catalog> getCatalogByAlias(String catalogAlias, String siteId) {
    return fetchData(siteId, connection -> {
      StoreContext storeContext = connection.getInitialStoreContext();
      CatalogService catalogService = connection.getCatalogService();
      return catalogService
              .getCatalog(CatalogAlias.of(catalogAlias), storeContext)
              .orElse(null);
    });
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCategory(#categoryId, #siteId)")
  public DataFetcherResult<Category> getCategory(String categoryId, String siteId) {
    return fetchData(siteId, connection -> {
      CommerceId commerceId = getCategoryId(categoryId, connection);
      CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getInitialStoreContext());
      return (Category) bean;
    });
  }

  @SuppressWarnings("unused")
  @Deprecated
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
    CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getInitialStoreContext());
    return builder.data((Category) bean).build();
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCommerceBean(#commerceId, #siteId)")
  @Nullable
  public DataFetcherResult<CommerceBean> getCommerceBean(String commerceId, String siteId) {
    return fetchData(siteId, connection -> {

      //Check if the id is a product first
      CommerceId possibleProduct = getProductId(commerceId, connection);
      CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(possibleProduct, connection.getInitialStoreContext());
      try {
        if (bean != null) {
          bean.load();
        }
      } catch (Exception e) {
        //It might be a category
        CommerceId possibleCategory = getCategoryId(commerceId, connection);
        bean = connection.getCommerceBeanFactory().createBeanFor(possibleCategory, connection.getInitialStoreContext());
      }
      return bean;
    });
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getProduct(#externalId, #siteId)")
  public DataFetcherResult<Product> getProduct(String externalId, String siteId) {
    return fetchData(siteId, connection -> {
      CommerceId commerceId = getProductId(externalId, connection);
      CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getInitialStoreContext());
      return (Product) bean;
    });
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.searchProducts(#searchTerm, #categoryId...)")
  public DataFetcherResult<SearchResult<Product>> searchProductsFilterByCategoryId(String searchTerm,
                                                                                   @Nullable String categoryId,
                                                                                   @Nullable String orderBy,
                                                                                   @Nullable Integer offset,
                                                                                   @Nullable Integer limit,
                                                                                   @Nullable List<String> filterFacets,
                                                                                   String siteId) {
    return fetchData(siteId, connection -> getProductSearchResult(searchTerm, orderBy, offset, limit, filterFacets, connection, getCategoryId(categoryId, connection)));
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.searchProducts(#searchTerm, #categoryId...)")
  public DataFetcherResult<SearchResult<Product>> searchProductsFilterByCategorySeoSegment(String searchTerm,
                                                                                           @Nullable String categorySeoSegment,
                                                                                           @Nullable String orderBy,
                                                                                           @Nullable Integer offset,
                                                                                           @Nullable Integer limit,
                                                                                           @Nullable List<String> filterFacets,
                                                                                           String siteId) {
    return fetchData(siteId, connection -> {
      CommerceId commerceId = null;
      //The commerceId needs to be an externalId or an externalTechId due to GrpcUtils#entityIdFrom
      if (StringUtils.isNotBlank(categorySeoSegment)) {
        StoreContext storeContext = connection.getInitialStoreContext();
        Category categoryBySeoSegment = connection.getCatalogService().findCategoryBySeoSegment(categorySeoSegment, storeContext);
        if (categoryBySeoSegment != null) {
          commerceId = connection.getIdProvider().formatCategoryId(storeContext.getCatalogAlias(), categoryBySeoSegment.getExternalId());
        }
      }

      return getProductSearchResult(searchTerm, orderBy, offset, limit, filterFacets, connection, commerceId);
    });
  }

  @Nullable
  public SearchResult<ProductVariant> searchProductVariants(String searchTerm, Map<String, String> searchParams, String siteId) {
    CommerceConnection connection = getCommerceConnection(siteId);
    if (connection == null) {
      return null;
    }
    StoreContext storeContext = connection.getInitialStoreContext();

    try {
      CatalogService catalogService = connection.getCatalogService();
      SearchQueryBuilder builder = SearchQuery.builder(searchTerm, BaseCommerceBeanType.SKU);
      return catalogService.search(builder.build(), storeContext);
    } catch (CommerceException e) {
      LOG.warn("Could not search product variants with searchTerm {}", searchTerm, e);
      return null;
    }
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.findProductBySeoSegment(#seoSegment, #siteId)")
  public DataFetcherResult<Product> findProductBySeoSegment(String seoSegment, String siteId) {
    return fetchData(siteId, connection -> {
      StoreContext storeContext = connection.getInitialStoreContext();
      CatalogService catalogService = connection.getCatalogService();
      return catalogService.findProductBySeoSegment(seoSegment, storeContext);
    });
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.findCategoryBySeoSegment(#seoSegment, #siteId)")
  public DataFetcherResult<Category> findCategoryBySeoSegment(String seoSegment, String siteId) {
    return fetchData(siteId, connection -> {
      StoreContext storeContext = connection.getInitialStoreContext();
      CatalogService catalogService = connection.getCatalogService();
      return catalogService.findCategoryBySeoSegment(seoSegment, storeContext);
    });
  }

  @SuppressWarnings("unused")
  @Deprecated
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getProductVariant(#externalId, #siteId)")
  public DataFetcherResult<ProductVariant> getProductVariant(String productVariantId, String siteId) {
    return fetchData(siteId, connection -> {
      CommerceId commerceId = getProductVariantId(productVariantId, connection);
      CommerceBean bean = connection.getCommerceBeanFactory().createBeanFor(commerceId, connection.getInitialStoreContext());
      return (ProductVariant) bean;
    });
  }


  @Nullable
  private CommerceBean createCommerceBean(CommerceId commerceId, CommerceConnection commerceConnection) {
    StoreContext storeContext = commerceConnection.getInitialStoreContext();
    return commerceConnection.getCommerceBeanFactory().createBeanFor(commerceId, storeContext);
  }

  @Nullable
  private <T extends CommerceBean> T createCommerceBean(String id, CommerceConnection connection, Class<T> expectedType) {
    CommerceBean bean = createCommerceBean(id, connection);
    if (bean == null || !expectedType.isAssignableFrom(bean.getClass())) {
      return null;
    }
    return (T) bean;
  }

  @Nullable
  private CommerceBean createCommerceBean(String id, CommerceConnection commerceConnection) {
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(id);
    CommerceIdProvider idProvider = commerceConnection.getIdProvider();
    CatalogAlias catalogAlias = commerceConnection.getInitialStoreContext().getCatalogAlias();
    if (commerceIdOptional.isEmpty()) {
      LOG.debug("unknown id: '{}'", id);
      //Type guessing
      //Am i a product?
      CommerceBean commerceBean = createCommerceBean(idProvider.formatProductId(catalogAlias, id), commerceConnection);
      if (commerceBean != null) {
        return commerceBean;
      }
      //I might be a category
      return createCommerceBean(idProvider.formatCategoryId(catalogAlias, id), commerceConnection);
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
    CatalogAlias catalogAlias = connection.getInitialStoreContext().getCatalogAlias();
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(productId);
    return commerceIdOptional.orElseGet(() -> idProvider.formatProductId(catalogAlias, productId));
  }

  @NonNull
  private static CommerceId getProductVariantId(String productVariantId, CommerceConnection connection) {
    CommerceIdProvider idProvider = connection.getIdProvider();
    CatalogAlias catalogAlias = connection.getInitialStoreContext().getCatalogAlias();
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
  private CommerceId getCategoryId(@Nullable String categoryId, CommerceConnection connection) {
    CommerceIdProvider idProvider = connection.getIdProvider();
    CatalogAlias catalogAlias = connection.getInitialStoreContext().getCatalogAlias();
    Optional<CommerceId> commerceIdOptional = CommerceIdParserHelper.parseCommerceId(categoryId);
    return commerceIdOptional.orElseGet(() -> idProvider.formatCategoryId(catalogAlias, categoryId));
  }

  @SuppressWarnings("unused")
  // it is being used by within commerce-schema.graphql as @fetch(from: "@commerceLabsFacade.getCommerceId(#this)")
  @Nullable
  public static String getCommerceId(CommerceBean commerceBean) {
    return CommerceIdFormatterHelper.format(commerceBean.getId());
  }

  private SearchResult<Product> getProductSearchResult(String searchTerm, String orderBy, Integer offset, Integer limit, List<String> filterFacets, CommerceConnection connection, CommerceId categoryCommerceId) {
    StoreContext storeContext = connection.getInitialStoreContext();

    SearchQueryBuilder queryBuilder = SearchQuery.builder(searchTerm, BaseCommerceBeanType.PRODUCT);
    if (offset != null) {
      queryBuilder.setOffset(offset);
    }
    if (limit != null) {
      queryBuilder.setLimit(limit);
    }
    if (categoryCommerceId != null) {
      queryBuilder.setCategoryId(categoryCommerceId);
    }
    queryBuilder.setIncludeResultFacets(true);
    if (StringUtils.isNotBlank(orderBy)) {
      queryBuilder.setOrderBy(OrderBy.of(orderBy));
    }
    if (filterFacets != null) {
      queryBuilder.setFilterFacets(filterFacets.stream().map(SearchQueryFacet::of).collect(Collectors.toList()));
    }

    return connection.getCatalogService().search(queryBuilder.build(), storeContext);
  }

}
