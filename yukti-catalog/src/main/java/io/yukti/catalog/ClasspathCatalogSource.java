package io.yukti.catalog;

import io.yukti.core.api.Catalog;
import io.yukti.core.api.CatalogSource;

import java.io.InputStream;

public class ClasspathCatalogSource implements CatalogSource {
    private final String resourcePath;

    public ClasspathCatalogSource(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public String id() {
        return "classpath:" + resourcePath;
    }

    @Override
    public Catalog load(String version) throws Exception {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + resourcePath);
            return new JsonCatalogParser().parse(in);
        }
    }
}
