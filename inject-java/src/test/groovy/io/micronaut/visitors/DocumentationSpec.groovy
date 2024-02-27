package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

class DocumentationSpec extends AbstractTypeElementSpec {

    void "test read class level documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    /**This is property level docs
     */
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is method level docs*/
    String getTenant() {
        return tenant;
    }

    /**
        This is method level docs

     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getDocumentation().get() == 'This is class level docs'
        classElement.getFields().get(0).getDocumentation().get() == 'This is property level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("getTenant")).get(0).getDocumentation().get() == 'This is method level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("setTenant")).get(0).getDocumentation().get() == 'This is method level docs'
    }

    void "test read inherited documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test implements TestInterface {
    /**
     * This is property level docs
     */
    @NotBlank
    @NotNull
    private String tenant;

    @Override
    public String getTenant() {
        return tenant;
    }

    @Override
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}

interface TestInterface extends TestInterfaceB {
    /**
     * This is inherited method level docs
     */
    String getTenant();
}

interface TestInterfaceB {
    /**
     * This should be ignored
     */
    String getTenant();

    /**
     * This is deeply inherited method level docs
     */
    void setTenant(String tenant);

}
""")

        expect:
        classElement.getDocumentation().get() == 'This is class level docs'
        classElement.getFields().get(0).getDocumentation().get() == 'This is property level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("getTenant")).get(0).getDocumentation().get() == 'This is inherited method level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("setTenant")).get(0).getDocumentation().get() == 'This is deeply inherited method level docs'
    }
}
