package io.depguard;

import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.core.domain.JavaModifier.PRIVATE;
import static com.tngtech.archunit.core.domain.JavaModifier.STATIC;

import com.enofex.taikai.Taikai;
import com.enofex.taikai.TaikaiRule;
import com.enofex.taikai.java.ImportsConfigurer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class ArchUnitTests {

    private static final String BASE_PACKAGE = "io.depguard";

    @Test
    void shouldFulfillConstraints() {
        Taikai.builder()
                .namespace(BASE_PACKAGE)
                .java(java -> java
                        // Maven 3.9's ModelResolver contract forces the deprecated-shim ModelSource type in
                        // io.depguard.dependency.MavenModelResolver; revisited when Maven Resolver 2.x is adopted.
                        .noUsageOfDeprecatedAPIs(TaikaiRule.Configuration.of(
                                BASE_PACKAGE, List.of("io.depguard.dependency.MavenModelResolver")))
                        .methodsShouldNotDeclareGenericExceptions()
                        .utilityClassesShouldBeFinalAndHavePrivateConstructor()
                        .imports(ImportsConfigurer::shouldHaveNoCycles)
                        .naming(naming -> naming.fieldsShouldNotMatch(".*(List|Set|Map)$")
                                .constantsShouldFollowConventions()
                                .interfacesShouldNotHavePrefixI()))
                .logging(logging ->
                        logging.loggersShouldFollowConventions(Logger.class, "LOG", List.of(PRIVATE, STATIC, FINAL)))
                .test(test -> test.junit(junit -> junit.classesShouldBePackagePrivate(".*Test(s)")
                        .classesShouldNotBeAnnotatedWithDisabled()
                        .methodsShouldNotBeAnnotatedWithDisabled()))
                .spring(spring -> spring.noAutowiredFields()
                        .boot(boot -> boot.applicationClassShouldResideInPackage(BASE_PACKAGE))
                        .configurations(c -> c.namesShouldMatch(".+Config"))
                        .controllers(controllers -> controllers
                                .shouldBeAnnotatedWithRestController()
                                .namesShouldEndWithController()
                                .shouldNotDependOnOtherControllers()
                                .shouldBePackagePrivate())
                        .services(services -> services.shouldBeAnnotatedWithService()
                                .shouldNotDependOnControllers()
                                .namesShouldEndWithService())
                        .repositories(repositories ->
                                repositories.shouldNotDependOnServices().namesShouldEndWithRepository()))
                .build()
                .checkAll();
    }
}
