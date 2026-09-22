package io.depguard.test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Minimal {@link ResourceLoader} for unit tests that need to load classpath resources. */
public class TestResourceLoader implements ResourceLoader {

    @Override
    public Resource getResource(String location) {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            return new ClassPathResource(path, getClassLoader());
        }
        throw new IllegalArgumentException("Only classpath: locations are supported: " + location);
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }
}
