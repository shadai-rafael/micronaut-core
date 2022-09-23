/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Models a {@link PropertyElement} for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class JavaPropertyElement extends AbstractJavaElement implements PropertyElement {

    private final ClassElement type;
    private final String name;
    private final AccessKind readAccessKind;
    private final AccessKind writeAccessKind;
    private final ClassElement owningElement;
    @Nullable
    private final MethodElement getter;
    @Nullable
    private final MethodElement setter;
    @Nullable
    private final FieldElement field;
    private final boolean excluded;
    private final List<MemberElement> elements;
    private final AnnotationMetadata annotationMetadata;

    JavaPropertyElement(ClassElement owningElement,
                        ClassElement type,
                        MethodElement getter,
                        MethodElement setter,
                        FieldElement field,
                        ElementAnnotationMetadataFactory annotationMetadataFactory,
                        String name,
                        AccessKind readAccessKind,
                        AccessKind writeAccessKind,
                        boolean excluded,
                        JavaVisitorContext visitorContext) {
        super(selectNativeType(getter, setter, field), annotationMetadataFactory, visitorContext);
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.field = field;
        this.name = name;
        this.readAccessKind = readAccessKind;
        this.writeAccessKind = writeAccessKind;
        this.owningElement = owningElement;
        this.excluded = excluded;
        elements = new ArrayList<>(3);
        if (getter != null) {
            elements.add(getter);
        }
        if (setter != null) {
            elements.add(setter);
        }
        if (field != null) {
            elements.add(field);
        }
        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        if (elements.size() == 1) {
            annotationMetadata = elements.iterator().next();
        } else {
            annotationMetadata = new AnnotationMetadataHierarchy(
                true,
                elements.stream().map(e -> {
                    if (e instanceof MethodElement) {
                        return new AnnotationMetadataDelegate() {
                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                // Exclude type metadata
                                return e.getAnnotationMetadata().getDeclaredMetadata();
                            }
                        };
                    }
                    return e;
                }).toArray(AnnotationMetadata[]::new)
            );
        }
    }

    private static Element selectNativeType(MethodElement getter,
                                            MethodElement setter,
                                            FieldElement field) {
        if (getter != null) {
            return (Element) getter.getNativeType();
        }
        if (setter != null) {
            return (Element) setter.getNativeType();
        }
        if (field != null) {
            return (Element) field.getNativeType();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isExcluded() {
        return excluded;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return type; // Already generic
    }

    @Override
    public Optional<FieldElement> getField() {
        return Optional.ofNullable(field);
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        return Optional.ofNullable(setter);
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        return Optional.ofNullable(getter);
    }

    @Override
    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    @Override
    public boolean isArray() {
        return getType().isArray();
    }

    @Override
    public int getArrayDimensions() {
        return getType().getArrayDimensions();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }


    @Override
    public AccessKind getReadAccessKind() {
        return readAccessKind;
    }

    @Override
    public AccessKind getWriteAccessKind() {
        return writeAccessKind;
    }

    @Override
    public boolean isReadOnly() {
        switch (readAccessKind) {
            case METHOD:
                return setter == null;
            case FIELD:
                return field == null || field.isFinal();
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isWriteOnly() {
        switch (writeAccessKind) {
            case METHOD:
                return getter == null;
            case FIELD:
                return field == null;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public ClassElement getDeclaringType() {
        if (field != null) {
            return field.getDeclaringType();
        }
        if (getter != null) {
            return getter.getDeclaringType();
        }
        if (setter != null) {
            return setter.getDeclaringType();
        }
        throw new IllegalStateException();
    }

    @Override
    public ClassElement getOwningType() {
        return owningElement;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(AnnotationValue<T> annotationValue) {
        for (MemberElement memberElement : elements) {
            memberElement.annotate(annotationValue);
        }
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        for (MemberElement memberElement : elements) {
            memberElement.annotate(annotationType, consumer);
        }
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType) {
        for (MemberElement memberElement : elements) {
            memberElement.annotate(annotationType);
        }
        return this;
    }

    @Override
    public io.micronaut.inject.ast.Element annotate(String annotationType) {
        for (MemberElement memberElement : elements) {
            memberElement.annotate(annotationType);
        }
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        for (MemberElement memberElement : elements) {
            memberElement.annotate(annotationType, consumer);
        }
        return this;
    }

    @Override
    public io.micronaut.inject.ast.Element removeAnnotation(String annotationType) {
        for (MemberElement memberElement : elements) {
            memberElement.removeAnnotation(annotationType);
        }
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        for (MemberElement memberElement : elements) {
            memberElement.removeAnnotationIf(predicate);
        }
        return this;
    }

    @Override
    public io.micronaut.inject.ast.Element replaceAnnotations(AnnotationMetadata annotationMetadata) {
        throw new IllegalStateException("Not supported operation!");
    }

}
