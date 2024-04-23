package com.coremedia.blueprint.headlessserver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        CommerceCaasConfig.class,
})
public class CommerceCaasAutoConfiguration {
}
