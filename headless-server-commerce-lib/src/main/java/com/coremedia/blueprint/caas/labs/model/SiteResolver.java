package com.coremedia.blueprint.caas.labs.model;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.CommerceConnectionSupplier;
import com.coremedia.cache.Cache;
import com.coremedia.cap.multisite.Site;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.livecontext.ecommerce.common.CommerceConnection;
import com.coremedia.livecontext.ecommerce.common.CommerceException;
import com.coremedia.livecontext.ecommerce.common.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.stream.Collectors.toSet;

public class SiteResolver {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());

  private final SitesService sitesService;
  private final CommerceConnectionSupplier commerceConnectionSupplier;
  private final Cache cache;

  public SiteResolver(SitesService sitesService, CommerceConnectionSupplier commerceConnectionSupplier, Cache cache) {
    this.sitesService = sitesService;
    this.commerceConnectionSupplier = commerceConnectionSupplier;
    this.cache = cache;
  }

  Optional<Site> findSiteFor(String storeId, Locale locale) {
    return cache.get(new StoreIdAndLocaleToSiteCacheKey(storeId, locale, this));
  }

  Optional<Site> findSiteForUncached(String storeId, Locale locale) {
    Set<Site> matchingSites = sitesService.getSites().stream()
            .filter(site -> localeMatchesSite(site, locale))
            .filter(site -> siteHasStore(site, storeId))
            .collect(toSet());

    int matchingSitesCount = matchingSites.size();

    if (matchingSitesCount > 1) {
      throw new IllegalStateException("Found more than one site for store.id: " + storeId + " and locale: " + locale);
    }

    if (matchingSitesCount == 0) {
      LOG.warn("No site found with store.id={} and locale={}", storeId, locale);
      return Optional.empty();
    }

    Site site = matchingSites.iterator().next();
    LOG.debug("Found site {}({}) for store.id={} and locale={}", site.getName(), site.getLocale(), storeId, locale);
    return Optional.of(site);
  }

  // --- internal ---------------------------------------------------

  private boolean siteHasStore(Site site, String storeId) {
    StoreContext storeContext;

    try {
      Optional<CommerceConnection> commerceConnection = commerceConnectionSupplier.findConnection(site);

      if (commerceConnection.isEmpty()) {
        LOG.debug("Site '{}' has no commerce connection.", site.getName());
        return false;
      }

      storeContext = commerceConnection.get().getStoreContext();
    } catch (CommerceException e) {
      LOG.debug("Could not retrieve store context for site '{}'.", site.getName(), e);
      return false;
    }

    return storeId.equalsIgnoreCase(String.valueOf(storeContext.getStoreId()));
  }

  private static boolean localeMatchesSite(Site site, Locale locale) {
    Locale siteLocale = site.getLocale();
    return locale.equals(siteLocale) ||
            (isNullOrEmpty(siteLocale.getCountry()) && locale.getLanguage().equals(siteLocale.getLanguage()));
  }
}
