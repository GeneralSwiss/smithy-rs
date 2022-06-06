/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.smithy.testutil

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.server.smithy.RustCodegenServerPlugin
import software.amazon.smithy.rust.codegen.server.smithy.generators.ServerBuilderGenerator
import software.amazon.smithy.rust.codegen.smithy.CodegenConfig
import software.amazon.smithy.rust.codegen.smithy.CodegenContext
import software.amazon.smithy.rust.codegen.smithy.RustSettings
import software.amazon.smithy.rust.codegen.smithy.RustSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitorConfig
import software.amazon.smithy.rust.codegen.smithy.generators.CodegenTarget
import software.amazon.smithy.rust.codegen.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.implBlock
import software.amazon.smithy.rust.codegen.testutil.TestRuntimeConfig
import software.amazon.smithy.rust.codegen.testutil.testRustSettings

val ServerTestSymbolVisitorConfig = SymbolVisitorConfig(
    runtimeConfig = TestRuntimeConfig,
    // These are the settings we default to if the user does not override them in their `smithy-build.json`.
    codegenConfig = CodegenConfig(
        renameExceptions = false,
        includeFluentClient = false,
        addMessageToErrors = false,
        formatTimeoutSeconds = 20,
        eventStreamAllowList = emptySet()
    ),
    handleRustBoxing = true,
    handleRequired = true
)

fun serverTestCodegenContext(
    model: Model,
    serviceShape: ServiceShape? = null,
    settings: RustSettings = testRustSettings(),
    codegenTarget: CodegenTarget = CodegenTarget.SERVER
): CodegenContext = CodegenContext(
    model,
    serverTestSymbolProvider(model),
    TestRuntimeConfig,
    serviceShape
        ?: model.serviceShapes.firstOrNull()
        ?: ServiceShape.builder().version("test").id("test#Service").build(),
    ShapeId.from("test#Protocol"),
    settings,
    codegenTarget
)

fun serverTestSymbolProvider(
    model: Model,
    serviceShape: ServiceShape? = null,
    publicConstrainedTypesEnabled: Boolean = true
): RustSymbolProvider =
    RustCodegenServerPlugin.baseSymbolProvider(
        model,
        serviceShape ?: ServiceShape.builder().version("test").id("test#Service").build(),
        ServerTestSymbolVisitorConfig,
        publicConstrainedTypesEnabled = publicConstrainedTypesEnabled
    )

/**
 * In tests, we frequently need to generate a struct, a builder, and an impl block to access said builder.
 */
fun StructureShape.serverRenderWithModelBuilder(model: Model, symbolProvider: RustSymbolProvider, writer: RustWriter) {
    StructureGenerator(model, symbolProvider, writer, this).render(CodegenTarget.SERVER)
    val modelBuilder = ServerBuilderGenerator(serverTestCodegenContext(model), this)
    modelBuilder.render(writer)
    writer.implBlock(this, symbolProvider) {
        modelBuilder.renderConvenienceMethod(this)
    }
}
