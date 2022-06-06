/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.server.smithy

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.traits.LengthTrait
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.smithy.Models
import software.amazon.smithy.rust.codegen.smithy.RustSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.WrappingSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.contextName
import software.amazon.smithy.rust.codegen.smithy.handleOptionality
import software.amazon.smithy.rust.codegen.smithy.handleRustBoxing
import software.amazon.smithy.rust.codegen.smithy.isDirectlyConstrained
import software.amazon.smithy.rust.codegen.smithy.locatedIn
import software.amazon.smithy.rust.codegen.smithy.rustType
import software.amazon.smithy.rust.codegen.smithy.symbolBuilder
import software.amazon.smithy.rust.codegen.util.hasTrait
import software.amazon.smithy.rust.codegen.util.toPascalCase

/**
 * The [ConstrainedShapeSymbolProvider] returns, for a given _directly_
 * constrained shape, a symbol whose Rust type can hold the constrained values.
 *
 * For all shapes with supported traits directly attached to them, this type is
 * a [RustType.Opaque] wrapper tuple newtype holding the inner constrained
 * type.
 *
 * The symbols this symbol provider returns are always public and exposed to
 * the end user.
 *
 * This symbol provider is meant to be used "deep" within the wrapped symbol
 * providers chain, just above the core base symbol provider, `SymbolVisitor`.
 *
 * If the shape is _transitively but not directly_ constrained, use
 * [PubCrateConstrainedShapeSymbolProvider] instead, which returns symbols
 * whose associated types are `pub(crate)` and thus not exposed to the end
 * user.
 */
class ConstrainedShapeSymbolProvider(
    private val base: RustSymbolProvider,
    private val model: Model,
    private val serviceShape: ServiceShape,
) : WrappingSymbolProvider(base) {
    private val nullableIndex = NullableIndex.of(model)

    private fun publicConstrainedSymbolForMapShape(shape: Shape): Symbol {
        check(shape is MapShape)

        val rustType = RustType.Opaque(shape.contextName(serviceShape).toPascalCase())
        return symbolBuilder(shape, rustType).locatedIn(Models).build()
    }

    override fun toSymbol(shape: Shape): Symbol {
        return when (shape) {
            is MemberShape -> {
                // TODO(https://github.com/awslabs/smithy-rs/issues/1401) Member shapes can have constraint traits
                //  (constraint trait precedence).
                val target = model.expectShape(shape.target)
                val targetSymbol = this.toSymbol(target)
                // Handle boxing first so we end up with `Option<Box<_>>`, not `Box<Option<_>>`.
                handleOptionality(handleRustBoxing(targetSymbol, shape), shape, nullableIndex)
            }
            is MapShape -> {
                if (shape.isDirectlyConstrained(base)) {
                    check(shape.hasTrait<LengthTrait>()) { "Only the `length` constraint trait can be applied to maps" }
                    publicConstrainedSymbolForMapShape(shape)
                } else {
                    val keySymbol = this.toSymbol(shape.key)
                    val valueSymbol = this.toSymbol(shape.value)
                    symbolBuilder(shape, RustType.HashMap(keySymbol.rustType(), valueSymbol.rustType()))
                        .addReference(keySymbol)
                        .addReference(valueSymbol)
                        .build()
                }
            }
            is CollectionShape -> {
                // TODO(https://github.com/awslabs/smithy-rs/issues/1401) Both arms return the same because we haven't
                //  implemented any constraint trait on collection shapes yet.
                if (shape.isDirectlyConstrained(base)) {
                    val inner = this.toSymbol(shape.member)
                    symbolBuilder(shape, RustType.Vec(inner.rustType())).addReference(inner).build()
                } else {
                    val inner = this.toSymbol(shape.member)
                    symbolBuilder(shape, RustType.Vec(inner.rustType())).addReference(inner).build()
                }
            }
            is StringShape -> {
                if (shape.isDirectlyConstrained(base)) {
                    val rustType = RustType.Opaque(shape.contextName(serviceShape).toPascalCase())
                    symbolBuilder(shape, rustType).locatedIn(Models).build()
                } else {
                    base.toSymbol(shape)
                }
            }
            else -> base.toSymbol(shape)
        }
    }
}
