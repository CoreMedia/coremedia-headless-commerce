package com.coremedia.blueprint.boot.headlessserver;

import com.coremedia.caas.richtext.stax.config.InvalidDefinition;
import com.coremedia.util.Hooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the headless server application.
 */
@SpringBootApplication( excludeName = {
        "net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration",
        "net.devh.boot.grpc.client.autoconfigure.GrpcClientHealthAutoConfiguration",
        "net.devh.boot.grpc.client.autoconfigure.GrpcClientMetricAutoConfiguration",
})
public class HeadlessServerApp {

  private static final Logger LOG = LoggerFactory.getLogger(HeadlessServerApp.class);

  private static Throwable unwrap(Throwable e) {
    return (e.getCause() != null) ? unwrap(e.getCause()) : e;
  }

  public static void main(String[] args) {
    try {
      Hooks.enable();
      SpringApplication.run(HeadlessServerApp.class, args);
    } catch (BeanCreationException e) {
      Throwable c = unwrap(e);
      if (InvalidDefinition.class.isAssignableFrom(c.getClass())) {
        LOG.error("Application startup failed, cause: {}", ((InvalidDefinition) c).getDetailMessage());
        return;
      }
      LOG.error("Application startup failed, cause: {}", e.getMessage());
    } finally {
      Hooks.disable();
    }
  }
}
