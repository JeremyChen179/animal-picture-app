package com.example.animalpictures.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Enforces the clean-architecture dependency rule: dependencies point inwards only,
 * adapter -> application -> domain, and the inner layers don't know about Spring or any adapter.
 */
class ArchitectureTest {

    private static final String BASE = "com.example.animalpictures";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void domainDependsOnNothingButJava() {
        classes().that().resideInAPackage(BASE + ".domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", BASE + ".domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDependsOnlyOnDomainAndJava() {
        classes().that().resideInAPackage(BASE + ".application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", BASE + ".domain..", BASE + ".application..")
                .because("use cases must not depend on Spring, HTTP, SQL or any adapter")
                .check(CLASSES);
    }

    @Test
    void webAdapterDoesNotUseOutboundAdapters() {
        noClasses().that().resideInAPackage(BASE + ".adapter.in..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".adapter.out..")
                .because("the web layer goes through the use cases")
                .check(CLASSES);
    }

    @Test
    void outboundAdaptersAreIndependentOfEachOther() {
        noClasses().that().resideInAPackage(BASE + ".adapter.out.persistence..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".adapter.out.pictures..")
                .check(CLASSES);
        noClasses().that().resideInAPackage(BASE + ".adapter.out.pictures..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".adapter.out.persistence..")
                .check(CLASSES);
    }

    @Test
    void onlyConfigWiresUseCasesToAdapters() {
        noClasses().that().resideOutsideOfPackages(BASE + ".config..", BASE + ".adapter..")
                .and().doNotHaveFullyQualifiedName(BASE + ".AnimalPictureApplication")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".adapter..")
                .check(CLASSES);
    }
}
