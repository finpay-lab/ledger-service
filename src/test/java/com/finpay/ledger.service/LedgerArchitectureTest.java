package com.finpay.ledger.service;

import static com.finpay.common.test.ArchitectureRules.domainIsIndependentOfInfrastructure;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the shared FinPay architecture rules (from com.finpay:common-test)
 * against the ledger domain: domain logic must stay free of Spring/JPA/Kafka.
 */
@AnalyzeClasses(packages = "com.finpay.ledger.service.domain")
class LedgerArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_independent_of_infrastructure = domainIsIndependentOfInfrastructure();
}