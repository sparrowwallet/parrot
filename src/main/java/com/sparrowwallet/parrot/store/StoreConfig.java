package com.sparrowwallet.parrot.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoreConfig {

    @Value("${store.implementation}")
    private String storeImplementation;

    private final Store javaStore;

    public StoreConfig(Store javaStore) {
        this.javaStore = javaStore;
    }

    @Bean
    public Store store() {
        switch (storeImplementation) {
            case "javaStore":
                return javaStore;
            default:
                throw new IllegalArgumentException("Invalid Store implementation: " + storeImplementation);
        }
    }
}
