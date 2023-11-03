package com.coremedia.blueprint.boot.headlessserver;

import com.coremedia.caas.richtext.stax.config.InvalidDefinition;
import com.coremedia.util.Hooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * Main entry point for the headless server application.
 */
@SpringBootApplication(excludeName = {
        "com.coremedia.blueprint.caas.p13n.P13nAutoConfiguration",
        // Exclude auto configurations from headless-server-web in favour of CommerceCaasAutoConfiguration.
        "com.coremedia.caas.web.CaasWebAutoConfiguration",
        "com.coremedia.caas.web.CaasMediaAutoConfiguration",
})
@ComponentScan("com.coremedia.cap.undoc.common.spring") // required because component loader in not active here
public class HeadlessServerCommerceApp {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());

  private static Throwable unwrap(Throwable e) {
    return (e.getCause() != null) ? unwrap(e.getCause()) : e;
  }

  public static void main(String[] args) {
    try {
      Hooks.enable();
      SpringApplication.run(HeadlessServerCommerceApp.class, args);
    } catch (BeanCreationException e) {
      Throwable c = unwrap(e);
      if (InvalidDefinition.class.isAssignableFrom(c.getClass())) {
        LOG.error("Application startup failed, cause: {}", ((InvalidDefinition) c).getDetailMessage());
        return;
      }
      LOG.error("Application startup failed, cause: {}", e.getMessage());
    } catch (Exception e) {
      LOG.error("Application startup failed, cause: {}", e.getMessage());
    } finally {
      Hooks.disable();
    }
  }
}
