package com.example.polybets.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Bu proje "hafif" (pragmatic) hexagonal mimariyi ArchUnit ile otomatik
 * olarak zorunlu kılar. Amaç: mimariyi sadece iddia etmemek, sınırların
 * ihlal edilmediğini her build'de test etmek.
 */
class HexagonalArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.polybets";

    private final com.tngtech.archunit.core.domain.JavaClasses importedClasses =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE_PACKAGE);

    @Test
    void domainKatmaniHicbirFrameworkeBagimliOlmamali() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..");

        rule.check(importedClasses);
    }

    @Test
    void domainAdapterKatmanindanBagimsizOlmali() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".adapter..");

        rule.check(importedClasses);
    }

    @Test
    void applicationKatmaniAdapterdanBagimsizOlmali() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".adapter..");

        rule.check(importedClasses);
    }

    @Test
    void adapterKatmanlariBirbirineDogrudanBagimliOlmamali() {
        ArchRule rule = classes()
                .that().resideInAPackage(BASE_PACKAGE + ".adapter.out..")
                .should().onlyDependOnClassesThat()
                .resideOutsideOfPackage(BASE_PACKAGE + ".adapter.in..");

        rule.check(importedClasses);
    }
}
