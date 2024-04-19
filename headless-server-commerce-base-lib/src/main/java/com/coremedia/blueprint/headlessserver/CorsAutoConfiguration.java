package com.coremedia.blueprint.headlessserver;

import com.coremedia.caas.web.cors.CorsConfigurationHelper;
import com.coremedia.caas.web.cors.CorsConfigurationProperties;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(CorsConfigurationProperties.class)
@DefaultAnnotation(NonNull.class)

/**
 * Patched copy of com.coremedia.caas.web.cors.CorsAutoConfiguration
 *
 * Avoids CORS Errors for preflight requests
 * see https://www.baeldung.com/spring-security-cors-preflight
 */
public class CorsAutoConfiguration implements WebMvcConfigurer {

  private final CorsConfigurationProperties corsConfigurationProperties;

  @SuppressWarnings("findbugs:EI_EXPOSE_REP2")
  public CorsAutoConfiguration(CorsConfigurationProperties corsConfigurationProperties) {
    this.corsConfigurationProperties = corsConfigurationProperties;
  }

  @Override
  public void addCorsMappings(CorsRegistry corsRegistry) {
    if (corsConfigurationProperties.isDisableProtection()) {
      corsRegistry.addMapping("/**");
    }
    CorsConfigurationHelper.applyCorsConfiguration(corsConfigurationProperties, corsRegistry);
  }
}
