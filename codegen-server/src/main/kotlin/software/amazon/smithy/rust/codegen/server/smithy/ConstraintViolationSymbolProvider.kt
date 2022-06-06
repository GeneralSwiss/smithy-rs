/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.server.smithy

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.rust.codegen.rustlang.RustReservedWords
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.smithy.Models
import software.amazon.smithy.rust.codegen.smithy.RustSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.Unconstrained
import software.amazon.smithy.rust.codegen.smithy.WrappingSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.canReachConstrainedShape
import software.amazon.smithy.rust.codegen.smithy.contextName
import software.amazon.smithy.rust.codegen.smithy.generators.builderSymbol
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.util.toSnakeCase

/**
 * The [ConstraintViolationSymbolProvider] returns, for a given constrained
 * shape, a symbol whose Rust type can hold information about constraint
 * violations that may occur when building the shape from unconstrained values.
 *
 * So, for example, given the model:
 *
 * ```smithy
 * @pattern("\\w+")
 * @length(min: 1, max: 69)
 * string NiceString
 *
 * structure Structure {
 *     @required
 *     niceString: NiceString
 * }
 * ```
 *
 * A `NiceString` built from an arbitrary Rust `String` may give rise to at
 * most two constraint trait violations: one for `pattern`, one for `length`.
 * Similarly, the shape `Structure` can fail to be built when a value for
 * `niceString` is not provided.
 *
 * Said type is always called `ConstraintViolation`, and resides in a bespoke
 * module inside the same module as the _public_ constrained type the user is
 * exposed to. When the user is _not_ exposed to the constrained type, the
 * constraint violation type's module is a child of the `model` module.
 *
 * It is the responsibility of the caller to ensure that the shape is
 * constrained (either directly or transitively) before using this symbol
 * provider. This symbol provider intentionally crashes if the shape is not
 * constrained.
 */
class ConstraintViolationSymbolProvider(
    private val base: RustSymbolProvider,
    private val model: Model,
    private val serviceShape: ServiceShape,
) : WrappingSymbolProvider(base) {
    private val constraintViolationName = "ConstraintViolation"

    private fun constraintViolationSymbolForCollectionOrMapOrUnionShape(shape: Shape): Symbol {
        check(shape is CollectionShape || shape is MapShape || shape is UnionShape)

        val symbol = base.toSymbol(shape)
        val constraintViolationNamespace =
            "${symbol.namespace.let { it.ifEmpty { "crate::${Models.namespace}" } }}::${
                RustReservedWords.escapeIfNeeded(
                    shape.contextName(serviceShape).toSnakeCase()
                )
            }"
        val rustType = RustType.Opaque(constraintViolationName, constraintViolationNamespace)
        return Symbol.builder()
            .rustType(rustType)
            .name(rustType.name)
            .namespace(rustType.namespace, "::")
            .definitionFile(symbol.definitionFile)
            .build()
    }

    override fun toSymbol(shape: Shape): Symbol {
        check(shape.canReachConstrainedShape(model, base))

        return when (shape) {
            is MapShape, is CollectionShape, is UnionShape -> {
                constraintViolationSymbolForCollectionOrMapOrUnionShape(shape)
            }
            is StructureShape -> {
                val builderSymbol = shape.builderSymbol(base)

                val namespace = builderSymbol.namespace
                val rustType = RustType.Opaque(constraintViolationName, namespace)
                Symbol.builder()
                    .rustType(rustType)
                    .name(rustType.name)
                    .namespace(rustType.namespace, "::")
                    .definitionFile(Unconstrained.filename)
                    .build()
            }
            is StringShape -> {
                val namespace = "crate::${Models.namespace}::${
                    RustReservedWords.escapeIfNeeded(
                        shape.contextName(serviceShape).toSnakeCase()
                    )
                }"
                val rustType = RustType.Opaque(constraintViolationName, namespace)
                Symbol.builder()
                    .rustType(rustType)
                    .name(rustType.name)
                    .namespace(rustType.namespace, "::")
                    .definitionFile(Models.filename)
                    .build()
            }
            else -> TODO("Constraint traits on other shapes not implemented yet: $shape")
        }
    }
}
