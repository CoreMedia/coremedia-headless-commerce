package com.coremedia.blueprint.headlessserver;

import com.coremedia.cap.common.CapConnection;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

class UapiConnectionReadinessHealthIndicator implements HealthIndicator {

  private final CapConnection connection;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  public UapiConnectionReadinessHealthIndicator(CapConnection connection) {
    this.connection = connection;
  }

  @Override
  public Health health() {
    if (!connection.isContentRepositoryAvailable()) {
      return Health.down().withDetail("content repository", "offline").build();
    } else if (connection.isContentRepositoryToBeUnavailable()) {
      return Health.down().withDetail("content repository", "will be offline soon").build();
    } else {
      return Health.up().withDetail("content repository", "OK").build();
    }
  }
}
