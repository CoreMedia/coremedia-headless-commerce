package com.coremedia.blueprint.caas.labs.model;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

@DefaultAnnotation(NonNull.class)
public class Metadata {
  String id;
  String externalId;
  String externalTechId;
  String seoPath;
  String locale;
  String storeId;
  String catalogId;
  String type;
  String pageTitle;
  String metaDescription;
  List<String> metaKeywords;

  public Metadata(String id, String externalId, String externalTechId, String seoPath,
                  String locale, String storeId, String catalogId,
                  String type, String pageTitle, String metaDescription, List<String> metaKeywords) {
    this.id = id;
    this.externalId = externalId;
    this.externalTechId = externalTechId;
    this.seoPath = seoPath;
    this.locale = locale;
    this.storeId = storeId;
    this.catalogId = catalogId;
    this.type = type;
    this.pageTitle = pageTitle;
    this.metaDescription = metaDescription;
    this.metaKeywords = metaKeywords;
  }
}
