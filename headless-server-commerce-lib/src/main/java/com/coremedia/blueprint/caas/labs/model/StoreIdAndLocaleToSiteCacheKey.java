package com.coremedia.blueprint.caas.labs.model;

import com.coremedia.cache.Cache;
import com.coremedia.cache.CacheKey;
import com.coremedia.cap.multisite.Site;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@DefaultAnnotation(NonNull.class)
class StoreIdAndLocaleToSiteCacheKey extends CacheKey<Optional<Site>> {

  private final String storeId;
  private final Locale locale;
  private final SiteResolver siteResolver;

  public StoreIdAndLocaleToSiteCacheKey(String storeId, Locale locale, SiteResolver siteResolver) {
    this.storeId = storeId;
    this.locale = locale;
    this.siteResolver = siteResolver;
  }

  @Override
  public Optional<Site> evaluate(Cache cache) {
    return siteResolver.findSiteForUncached(storeId, locale);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StoreIdAndLocaleToSiteCacheKey that = (StoreIdAndLocaleToSiteCacheKey) o;
    return storeId.equals(that.storeId) &&
            locale.equals(that.locale) &&
            siteResolver.equals(that.siteResolver);
  }

  @Override
  public int hashCode() {
    return Objects.hash(storeId, locale, siteResolver);
  }
}
