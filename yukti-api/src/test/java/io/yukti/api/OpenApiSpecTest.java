package io.yukti.api;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts that OpenAPI spec exists in build artifacts (META-INF/swagger/ on classpath).
 */
class OpenApiSpecTest {

    @Test
    void openApiSpecExistsOnClasspath() {
        URL url = OpenApiSpecTest.class.getClassLoader().getResource("META-INF/swagger/yukti-v1.yaml");
        assertNotNull(url, "OpenAPI spec yukti-v1.yaml should exist in META-INF/swagger/");
    }
}
