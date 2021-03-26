package com.coremedia.blueprint.caas.labs.model;

import com.coremedia.livecontext.ecommerce.search.SearchFacet;

import java.util.List;

public class ProductSearchFacetResult {
  private String facetName;
  private List<SearchFacet> searchFacets;

  public static ProductSearchFacetResult fromValues(String facetName, List<SearchFacet> searchFacets) {
    ProductSearchFacetResult result = new ProductSearchFacetResult();
    result.facetName = facetName;
    result.searchFacets = searchFacets;
    return result;
  }

  public String getFacetName() {
    return facetName;
  }

  public void setFacetName(String facetName) {
    this.facetName = facetName;
  }

  public List<SearchFacet> getSearchFacets() {
    return searchFacets;
  }

  public void setSearchFacets(List<SearchFacet> searchFacets) {
    this.searchFacets = searchFacets;
  }

}
