package com.finpay.ledger.service.architecture;

import com.finpay.common.test.ArchitectureRules;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;

/**
 * Rule 4 (AGENTS.md): domain logic must not depend on infrastructure frameworks.
 * Reuses the shared rule from com.finpay:common-test so every service checks the
 * same thing (only main classes are scanned).
 */
class DomainLayerArchitectureTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void domain_package_is_free_of_spring_jpa_and_kafka_imports() {
        ArchitectureRules.domainIsIndependentOfInfrastructure()
                .check(IMPORTER.importPackages("com.finpay.ledger.service"));
    }
}
