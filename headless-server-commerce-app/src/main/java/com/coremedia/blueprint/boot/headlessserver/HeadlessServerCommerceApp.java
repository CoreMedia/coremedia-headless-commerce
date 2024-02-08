package com.coremedia.blueprint.boot.headlessserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

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
  public static void main(String[] args) {
    SpringApplication.run(HeadlessServerCommerceApp.class, args);
  }
}
