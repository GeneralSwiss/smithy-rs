/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.smithy.generators.http

import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.rust.codegen.client.smithy.CoreCodegenContext
import software.amazon.smithy.rust.codegen.client.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.client.smithy.generators.http.HttpBindingGenerator
import software.amazon.smithy.rust.codegen.client.smithy.generators.http.HttpMessageType
import software.amazon.smithy.rust.codegen.client.smithy.protocols.Protocol

class ServerResponseBindingGenerator(
    protocol: Protocol,
    coreCodegenContext: CoreCodegenContext,
    operationShape: OperationShape,
) {
    private val httpBindingGenerator =
        HttpBindingGenerator(protocol, coreCodegenContext, coreCodegenContext.symbolProvider, operationShape)

    fun generateAddHeadersFn(shape: Shape): RuntimeType? =
        httpBindingGenerator.generateAddHeadersFn(shape, HttpMessageType.RESPONSE)
}
