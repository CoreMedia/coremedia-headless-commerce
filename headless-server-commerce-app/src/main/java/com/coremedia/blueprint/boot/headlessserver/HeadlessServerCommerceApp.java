package com.coremedia.blueprint.boot.headlessserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * Main entry point for the headless server application.
 */
@SpringBootApplication(excludeName = {
        // Exclude auto configurations from headless-server.cms-core autoconfiguration imports:
        // use CommerceCaasAutoConfiguration instead
        "com.coremedia.caas.web.CaasWebAutoConfiguration",
        // no media support
        "com.coremedia.caas.web.CaasMediaAutoConfiguration",
        // no metadata support
        "com.coremedia.caas.web.metadata.MetadataAutoConfiguration",
        // no P13n support
        "com.coremedia.blueprint.caas.p13n.P13nAutoConfiguration",
        // no plugin support
        "com.coremedia.cms.common.plugins.plugin_framework_autoconfiguration.PluginManagerAutoConfiguration",
        // no view support -> avoids error in ViewAutoConfiguration (CMS-24774)
        "com.coremedia.caas.web.view.impl.ViewAutoConfiguration",
        // no persisted queries -> avoids error in PersistedQueryAutoConfiguration (CMS-24775)
        "com.coremedia.caas.web.persistedqueries.impl.PersistedQueryAutoConfiguration",
        "com.coremedia.caas.web.rest.RestMappingAutoConfiguration",
})
@ComponentScan("com.coremedia.cap.undoc.common.spring") // required because component loader in not active here
@ServletComponentScan(basePackages = {
        "com.coremedia.caas.web.filter"
})
public class HeadlessServerCommerceApp {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());

  public static void main(String[] args) {
    SpringApplication.run(HeadlessServerCommerceApp.class, args);
  }
}
