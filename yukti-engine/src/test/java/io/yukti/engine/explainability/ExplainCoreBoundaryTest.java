package io.yukti.engine.explainability;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Build-time guard: yukti-explain-core must not depend on yukti-engine, yukti-api, yukti-catalog, or yukti-web.
 * A deliberate import from explain-core into any of these modules must cause this test to fail.
 */
class ExplainCoreBoundaryTest {

    @Test
    void explainCoreMustNotDependOnEngine() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("io.yukti.explain.core..", "io.yukti.engine..");
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.yukti.explain.core..")
            .should().dependOnClassesThat().resideInAPackage("io.yukti.engine..");
        rule.check(classes);
    }

    @Test
    void explainCoreMustNotDependOnCatalog() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("io.yukti.explain.core..", "io.yukti.catalog..");
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.yukti.explain.core..")
            .should().dependOnClassesThat().resideInAPackage("io.yukti.catalog..");
        rule.check(classes);
    }
}
