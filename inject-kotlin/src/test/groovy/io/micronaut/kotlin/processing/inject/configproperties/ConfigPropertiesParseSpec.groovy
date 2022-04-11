package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.kotlin.processing.inject.configuration.Engine
import spock.lang.Specification

import static io.micronaut.kotlin.processing.KotlinCompiler.*

class ConfigPropertiesParseSpec extends Specification {

    void "test inner class paths - pojo inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test

import io.micronaut.context.annotation.*
import java.time.Duration

@ConfigurationProperties("foo.bar")
class MyConfig {
    var host: String? = null
    
    @ConfigurationProperties("baz")
    open class ChildConfig: ParentConfig() {
        protected var stuff: String? = null
    }
}

open class ParentConfig {
    var foo: String? = null
}
''')
        then:
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'

        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.foo'
        beanDefinition.injectedMethods[1].name == 'setFoo'
    }

    void "test inner class paths - fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig {
    
    var host: String? = null
    
    @ConfigurationProperties("baz")
    open class ChildConfig {
        protected var stuff: String? = null
    }
}
''')
        then:
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inner class paths - one level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig {
    var host: String? = null
    
    @ConfigurationProperties("baz")
    class ChildConfig {
        var stuff: String? = null
    }
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }


    void "test inner class paths - two levels"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$MoreConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig {
    var host: String? = null
    
    @ConfigurationProperties("baz")
    class ChildConfig {
        var stuff: String? = null
        
        @ConfigurationProperties("more")
        class MoreConfig {
            var stuff: String? = null
        }
    }
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.more.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inner class paths - with parent inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig: ParentConfig() {
    var host: String? = null
    
    @ConfigurationProperties("baz")
    class ChildConfig {
        var stuff: String? = null
    }
}

@ConfigurationProperties("parent")
open class ParentConfig
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'parent.foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test setters with two arguments are not injected"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig {
    
    private var host: String = "localhost"

    fun getHost() = host

    fun setHost(host: String, port: Int) {
        this.host = host
    }
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 0
    }

    void "test setters with two arguments from abstract parent are not injected"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ChildConfig', '''
package test

import io.micronaut.context.annotation.*

abstract class MyConfig {
    private var host: String = "localhost"

    fun getHost() = host

    fun setHost(host: String, port: Int) {
        this.host = host
    }
}

@ConfigurationProperties("baz")
class ChildConfig: MyConfig() {
    var stuff: String? = null
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inheritance with setters"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ChildConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
open class MyConfig {
    protected var port: Int = 0
    var host: String? = null
}

@ConfigurationProperties("baz")
class ChildConfig: MyConfig() {
    var stuff: String? = null
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 3
        beanDefinition.injectedMethods[0].name == 'setStuff'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[1].name == 'setPort'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.port'
        beanDefinition.injectedMethods[2].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[2].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.host'
        beanDefinition.injectedMethods[2].name == 'setHost'

    }

    void "test annotation on property"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.HttpClientConfiguration', '''
package test

import io.micronaut.core.convert.format.*
import io.micronaut.context.annotation.*

@ConfigurationProperties("http.client")
class HttpClientConfiguration {
    @ReadableBytes
    var maxContentLength: Int = 1024 * 1024 * 10 // 10MB
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesize(ReadableBytes)
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo")
open class MyProperties {
    protected var fieldTest: String = "unconfigured"
    private val privateFinal = true
    protected val protectedFinal = true
    private var anotherField: Boolean = false
    private var internalField = "unconfigured"
    
    fun setSetterTest(s: String) {
        this.internalField = s
    }
    
    fun getSetter() = internalField
}
''')
        then:
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == 'setFieldTest'
        beanDefinition.injectedMethods[1].name == 'setSetterTest'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder().start()
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"

        when:
        applicationContext.environment.addPropertySource(
                "test",
                ['foo.setterTest' :'foo',
                'foo.fieldTest' :'bar']
        )
        bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"
    }

    void "test configuration properties inheritance from non-configuration properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo")
open class MyProperties: Parent() {
    protected var fieldTest: String = "unconfigured"
    private val privateFinal = true
    protected val protectedFinal = true
    private var anotherField: Boolean = false
    private var internalField = "unconfigured"
    
    fun setSetterTest(s: String) {
        this.internalField = s
    }
    
    fun getSetter() = internalField
}

open class Parent {
    private var parentField: String? = null
    
    fun setParentTest(s: String) {
        this.parentField = s
    }
    
    fun getParentTest() = parentField 
}
''')
        then:
        beanDefinition.injectedMethods.size() == 3
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.field-test'
        beanDefinition.injectedMethods[0].name == 'setFieldTest'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.setter-test'
        beanDefinition.injectedMethods[1].name == 'setSetterTest'
        beanDefinition.injectedMethods[2].name == 'setParentTest'
        beanDefinition.injectedMethods[2].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[2].getAnnotationMetadata().synthesize(Property).name() == 'foo.parent-test'


        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder().start()
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"

        when:
        applicationContext.environment.addPropertySource(
                "test",
                ['foo.setterTest' :'foo',
                'foo.fieldTest' :'bar']
        )
        bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"
    }

    void "test boolean fields starting with is[A-Z] map to set methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("micronaut.issuer.FooConfigurationProperties", """
package micronaut.issuer

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo")
class FooConfigurationProperties {

    private var issuer: String? = null
    private var isEnabled = false

    fun setIssuer(issuer: String) {
        this.issuer = issuer
    }
    
    //isEnabled field maps to setEnabled method
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }
}
""")
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].name == "setIssuer"
        beanDefinition.injectedMethods[1].name == "setEnabled"
    }

    void "test includes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*
        
@ConfigurationProperties(value = "foo", includes = ["publicField", "parentPublicField"])
class MyProperties: Parent() {
    var publicField: String? = null
    var anotherPublicField: String? = null
}

open class Parent {
    var parentPublicField: String? = null
    var anotherParentPublicField: String? = null
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setPublicField"
        beanDefinition.injectedMethods[1].name == "setParentPublicField"
    }

    void "test includes on methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", includes = ["publicMethod", "parentPublicMethod"])
class MyProperties: Parent() {

    fun setPublicMethod(value: String) {}
    fun setAnotherPublicMethod(value: String) {}
}

open class Parent {
    fun setParentPublicMethod(value: String) {}
    fun setAnotherParentPublicMethod(value: String) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[1].name == "setParentPublicMethod"
        beanDefinition.injectedMethods[0].name == "setPublicMethod"
    }

    void "test excludes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", excludes = ["anotherPublicField", "anotherParentPublicField"])
class MyProperties: Parent() {
    var publicField: String? = null
    var anotherPublicField: String? = null
}

open class Parent {
    var parentPublicField: String? = null
    var anotherParentPublicField: String? = null
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[1].name == "setParentPublicField"
        beanDefinition.injectedMethods[0].name == "setPublicField"
    }

    void "test excludes on methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", excludes = ["anotherPublicMethod", "anotherParentPublicMethod"])
class MyProperties: Parent() {

    fun setPublicMethod(value: String) {}
    fun setAnotherPublicMethod(value: String) {}
}

open class Parent {
    fun setParentPublicMethod(value: String) {}
    fun setAnotherParentPublicMethod(value: String) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[1].name == "setParentPublicMethod"
        beanDefinition.injectedMethods[0].name == "setPublicMethod"
    }

    void "test excludes on configuration builder"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*
import io.micronaut.kotlin.processing.inject.configuration.Engine

@ConfigurationProperties(value = "foo", excludes = ["engine", "engine2"])
class MyProperties: Parent() {

    @ConfigurationBuilder(prefixes = ["with"]) 
    val engine: Engine.Builder = Engine.builder()
    
    @ConfigurationBuilder(configurationPrefix = "two", prefixes = ["with"])
    var engine2: Engine.Builder = Engine.builder()
}

open class Parent {
    fun setEngine(engine: Engine.Builder) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.isEmpty()
        beanDefinition.injectedFields.isEmpty()

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'foo.manufacturer':'Subaru',
                'foo.two.manufacturer':'Subaru'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        ((Engine.Builder) bean.engine).build().manufacturer == 'Subaru'
        ((Engine.Builder) bean.getEngine2()).build().manufacturer == 'Subaru'
    }

    void "test name is correct with inner classes of non config props class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test\$TestNestedConfig", '''
package test

import io.micronaut.context.annotation.*

class Test {

    @ConfigurationProperties("test")
    class TestNestedConfig {
        var vall: String? = null
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].annotationMetadata.getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "test.vall"
    }

    void "test property names with numbers"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AwsConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("aws")
class AwsConfig {

    var disableEc2Metadata: String? = null
    var disableEcMetadata: String? = null
    var disableEc2instanceMetadata: String? = null
}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2-metadata"
        beanDefinition.injectedMethods[1].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec-metadata"
        beanDefinition.injectedMethods[2].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2instance-metadata"
    }

    void "test inner interface EachProperty list = true"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Parent$Child$Intercepted', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty

import jakarta.inject.Inject

@ConfigurationProperties("parent")
class Parent @Inject constructor(val children: List<Child>) {

    @EachProperty(value = "children", list = true)
    interface Child {
        fun getPropA(): String
        fun getPropB(): String
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(ConfigurationReader.class, "prefix").get() == "parent.children[*]"
        beanDefinition.getRequiredMethod("getPropA").getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "parent.children[*].prop-a"
    }

    void "test config props with post construct first in file"() {
        given:
        BeanContext context = buildContext("""
package test

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.annotation.PostConstruct

@ConfigurationProperties("app.entity")
class EntityProperties {

    @PostConstruct
    fun init() {
        println("prop = " + prop)
    }
    
    var prop: String? = null
}
""")

        when:
        context.getBean(context.classLoader.loadClass("test.EntityProperties"))

        then:
        noExceptionThrown()
    }
}
