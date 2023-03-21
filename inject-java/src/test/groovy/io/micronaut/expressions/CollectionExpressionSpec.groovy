package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import org.intellij.lang.annotations.Language

class CollectionExpressionSpec extends AbstractEvaluatedExpressionsSpec {

    void "test list dereference"() {
        given:
        @Language("java") def context = """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                List<Integer> getList() {
                    return List.of(1, 2, 3);
                }
            }
        """
        Object result = evaluateAgainstContext("#{list[1]}", context)
        Object result2 = evaluateAgainstContext("#{not empty list}", context)

        expect:
        result == 2
        result2 == true
    }

    void "test primitive array dereference"() {
        given:
        Object result = evaluateAgainstContext("#{array[1]}",
                """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                int[] getArray() {
                    return new int[] {1,2,3};
                }
            }
        """)

        expect:
        result == 2
    }

    void "test map dereference"() {
        given:
        @Language("java") def context = """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                Map<String, String> getMap() {
                    return Map.of(
                            "foo", "bar",
                            "baz", "stuff"
                    );
                }
            }
        """
        Object result = evaluateAgainstContext("#{map['foo']}",context)
        Object result2 = evaluateAgainstContext("#{not empty map}",context)

        expect:
        result == "bar"
        result2 == true
    }
}
