package com.coremedia.blueprint.caas.labs.model;

import com.coremedia.livecontext.ecommerce.common.StoreContext;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Currency;
import java.util.Locale;

@DefaultAnnotation(NonNull.class)
public class Store {
  String storeId;
  String storeName;
  String catalogId;
  String catalogAlias;
  Currency currency;
  Locale locale;
  String vendor;

  private Store(String storeId, String storeName, String catalogId, String catalogAlias, Currency currency, Locale locale, String vendor) {
    this.storeId = storeId;
    this.storeName = storeName;
    this.catalogId = catalogId;
    this.catalogAlias = catalogAlias;
    this.currency = currency;
    this.locale = locale;
    this.vendor = vendor;
  }

  static Store from(StoreContext storeContext, String vendor) {
    return new Store(
            storeContext.getStoreId(),
            storeContext.getStoreName(),
            storeContext.getCatalogId().orElseThrow().value(),
            storeContext.getCatalogAlias().value(),
            storeContext.getCurrency(),
            storeContext.getLocale(),
            vendor
    );
  }
}
