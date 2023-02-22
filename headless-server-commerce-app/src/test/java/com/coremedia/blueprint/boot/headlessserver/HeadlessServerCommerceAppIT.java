package com.coremedia.blueprint.boot.headlessserver;

import com.coremedia.blueprint.caas.labs.model.CommerceLabsFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "repository.factoryClassName=com.coremedia.cap.xmlrepo.XmlCapConnectionFactory",
        "com.coremedia.transform.blobCache.basePath=${user.dir}/target/blobCache",
})
@SuppressWarnings("SpringBootApplicationProperties")
class HeadlessServerCommerceAppIT {

  @Autowired
  private CommerceLabsFacade commerceLabsFacade;

  @Test
  void test() {
    assertThat(commerceLabsFacade).isNotNull();
  }
}
