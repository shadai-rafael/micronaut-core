/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.kotlin.processing.beans

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder

class KotlinConfigurationMetadataBuilder: ConfigurationMetadataBuilder<ClassElement>() {

    override fun getOriginatingElements(): Array<Element> {
        return Element.EMPTY_ELEMENT_ARRAY
    }

    override fun buildPropertyPath(
        owningType: ClassElement,
        declaringType: ClassElement,
        propertyName: String
    ): String {
        val type =  if (declaringType.hasStereotype(ConfigurationReader::class.java)) {
             declaringType
        } else {
            owningType
        }
        val prefix = type.stringValue(ConfigurationReader::class.java, "prefix")
            .filter(String::isNotEmpty)
            .orElseGet { buildTypePath(owningType, declaringType) }
        return "$prefix.$propertyName"
    }

    override fun buildTypePath(owningType: ClassElement, declaringType: ClassElement): String {
        val annotationMetadata = if (owningType.isInterface) {
            owningType.annotationMetadata
        } else {
            declaringType.annotationMetadata
        }
        return buildTypePath(owningType, declaringType, annotationMetadata)
    }

    override fun buildTypePath(
        owningType: ClassElement,
        declaringType: ClassElement,
        annotationMetadata: AnnotationMetadata
    ): String {
        val path = StringBuilder(calculateInitialPath(owningType, annotationMetadata))
        val element = if (owningType.isInterface) owningType else declaringType
        prependSuperclasses(element, path)
        var type = declaringType
        if (type.isInner) {
            // we have an inner class, so prepend inner class
            type = type.enclosingType.orElse(null)
            if (type != null) {
                val parentMetadata = type.annotationMetadata
                val prefix = parentMetadata.stringValue(ConfigurationReader::class.java, "prefix").orElse(null)
                if (prefix != null && prefix.isNotEmpty()) {
                    path.insert(0, "$prefix.")
                }
            }
        }
        return path.toString()
    }

    override fun getTypeString(type: ClassElement): String {
        return type.name.replace('$', '.')
    }

    override fun getAnnotationMetadata(type: ClassElement): AnnotationMetadata {
        return type.annotationMetadata
    }

    private fun prependSuperclasses(declaringType: ClassElement, path: StringBuilder) {
        var superType = getSuperClass(declaringType)
        while (superType != null) {
            val parentConfig = superType.stringValue(ConfigurationReader::class.java)
            if (parentConfig.isPresent) {
                path.insert(0, parentConfig.get() + '.')
                superType = getSuperClass(superType)
            } else {
                val parentPrefix = superType.stringValue(ConfigurationReader::class.java, "prefix")
                if (parentPrefix.isPresent) {
                    path.insert(0, parentPrefix.get() + '.')
                }
                break
            }
        }
    }

    private fun getSuperClass(declaringType: ClassElement): ClassElement? {
        return if (declaringType.isInterface) {
            declaringType.interfaces.find { it.hasStereotype(ConfigurationReader::class.java) }
        } else {
            declaringType.superType.orElse(null)
        }
    }

    private fun calculateInitialPath(owningType: ClassElement, annotationMetadata: AnnotationMetadata): String {
        return annotationMetadata.stringValue(ConfigurationReader::class.java)
            .map(pathEvaluationFunction(annotationMetadata))
            .orElseGet {
                owningType.stringValue(ConfigurationReader::class.java)
                    .map(pathEvaluationFunction(owningType.annotationMetadata))
                    .orElseGet {
                        pathEvaluationFunction(annotationMetadata).invoke("")
                    }
            }
    }

    private fun pathEvaluationFunction(annotationMetadata: AnnotationMetadata): (String) -> String {
        return { path: String ->
            if (annotationMetadata.hasDeclaredAnnotation(EachProperty::class.java)) {
                if (annotationMetadata.booleanValue(EachProperty::class.java, "list").orElse(false)) {
                    "$path[*]"
                } else {
                    "$path.*"
                }
            } else {
                val prefix = annotationMetadata.stringValue(ConfigurationReader::class.java, "prefix").orElse(null)
                if (StringUtils.isNotEmpty(prefix)) {
                    if (StringUtils.isEmpty(path)) {
                        prefix
                    } else {
                        "$prefix.$path"
                    }
                } else {
                    path
                }
            }
        }
    }
}
