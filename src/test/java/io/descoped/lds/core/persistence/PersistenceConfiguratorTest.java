package io.descoped.lds.core.persistence;

import io.descoped.config.DynamicConfiguration;
import io.descoped.config.StoreBasedDynamicConfiguration;
import io.descoped.lds.api.persistence.PersistenceInitializer;
import io.descoped.lds.api.persistence.ProviderName;
import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.descoped.lds.api.specification.Specification;
import io.descoped.lds.core.specification.JsonSchemaBasedSpecification;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class PersistenceConfiguratorTest {

    @Test
    public void thatServiceLoadingWorks() {
        DynamicConfiguration configuration = new StoreBasedDynamicConfiguration.Builder()
                .values("persistence.provider", "mem",
                        "persistence.initialization.max-wait-seconds", "0",
                        "persistence.mem.wait.min", "0",
                        "persistence.mem.wait.max", "0",
                        "persistence.fragment.capacity", "1024")
                .build();
        Specification specification = JsonSchemaBasedSpecification.create("spec/schemas/contact.json", "spec/schemas/provisionagreement.json");
        RxJsonPersistence persistence = PersistenceConfigurator.configurePersistence(configuration, specification);
        Assert.assertNotNull(persistence);
    }

    @Test
    public void thatInitializationFailsWhenConfigurationsAreMissing() {
        DynamicConfiguration configuration = new StoreBasedDynamicConfiguration.Builder()
                .values("persistence.provider", "mem")
                .build();
        Specification specification = JsonSchemaBasedSpecification.create("spec/schemas/contact.json", "spec/schemas/provisionagreement.json");
        try {
            PersistenceConfigurator.configurePersistence(configuration, specification);
            Assert.fail("Test should have failed because configurations are missing");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("persistence.fragment.capacity"));
        }
    }

    @Test
    public void thatAnnotationsAcrossServiceLoaderWorks() {
        String providerId = "mem";
        ServiceLoader<PersistenceInitializer> loader = ServiceLoader.load(PersistenceInitializer.class);
        List<ServiceLoader.Provider<PersistenceInitializer>> providers = loader.stream()
                .filter(p -> {
                    Class<? extends PersistenceInitializer> type = p.type();
                    ProviderName providerName = type.getDeclaredAnnotation(ProviderName.class);
                    return providerId.equals(providerName.value());
                })
                .collect(Collectors.toList());
        Assert.assertEquals(providers.size(), 1);
        Assert.assertEquals(providers.get(0).get().persistenceProviderId(), providerId);
    }

}
