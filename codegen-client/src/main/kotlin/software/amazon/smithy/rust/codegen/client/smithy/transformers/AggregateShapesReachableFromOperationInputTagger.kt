/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.client.smithy.transformers

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.SetShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.rust.codegen.core.smithy.traits.SyntheticAggregateShapeReachableFromOperationInputTagTrait
import software.amazon.smithy.rust.codegen.core.util.UNREACHABLE

/**
 * TODO Docs
 * TODO Move this to server or core.
 */
object AggregateShapesReachableFromOperationInputTagger {
    fun transform(model: Model): Model {
        val inputShapes = model.operationShapes.map {
            model.expectShape(it.inputShape, StructureShape::class.java)
        }
        val walker = Walker(model)
        val shapesReachableFromOperationInputs = inputShapes
            .flatMap { walker.walkShapes(it) }
            .toSet()

        return ModelTransformer.create().mapShapes(model) { shape ->
            when (shape) {
                is StructureShape, is UnionShape, is ListShape, is SetShape, is MapShape -> {
                    if (shapesReachableFromOperationInputs.contains(shape)) {
                        val builder = when (shape) {
                            is StructureShape -> shape.toBuilder()
                            is UnionShape -> shape.toBuilder()
                            is ListShape -> shape.toBuilder()
                            is SetShape -> shape.toBuilder()
                            is MapShape -> shape.toBuilder()
                            else -> UNREACHABLE("the `when` is exhaustive")
                        }
                        builder.addTrait(SyntheticAggregateShapeReachableFromOperationInputTagTrait()).build()
                    } else {
                        shape
                    }
                }
                else -> shape
            }
        }
    }
}
