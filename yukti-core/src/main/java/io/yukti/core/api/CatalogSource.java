package io.yukti.core.api;

/**
 * Loads catalog by version. Extensible: classpath, S3, etc.
 */
public interface CatalogSource {
    String id();
    Catalog load(String version) throws Exception;
}
