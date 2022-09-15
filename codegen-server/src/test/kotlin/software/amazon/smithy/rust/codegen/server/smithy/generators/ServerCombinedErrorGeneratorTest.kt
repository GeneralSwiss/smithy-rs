/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.smithy.generators

import org.junit.jupiter.api.Test
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.server.smithy.testutil.serverRenderWithModelBuilder
import software.amazon.smithy.rust.codegen.server.smithy.testutil.serverTestSymbolProvider
import software.amazon.smithy.rust.codegen.client.rustlang.RustModule
import software.amazon.smithy.rust.codegen.client.smithy.generators.error.ServerCombinedErrorGenerator
import software.amazon.smithy.rust.codegen.client.smithy.transformers.OperationNormalizer
import software.amazon.smithy.rust.codegen.client.testutil.TestWorkspace
import software.amazon.smithy.rust.codegen.client.testutil.asSmithyModel
import software.amazon.smithy.rust.codegen.client.testutil.compileAndTest
import software.amazon.smithy.rust.codegen.client.testutil.unitTest
import software.amazon.smithy.rust.codegen.client.util.lookup

class ServerCombinedErrorGeneratorTest {
    private val baseModel = """
        namespace error

        operation Greeting {
            errors: [InvalidGreeting, ComplexError, FooException, Deprecated]
        }

        @error("client")
        @retryable
        structure InvalidGreeting {
            @required
            message: String,
        }

        @error("server")
        structure FooException { }

        @error("server")
        structure ComplexError {
            abc: String,
            other: Integer
        }

        @error("server")
        @deprecated
        structure Deprecated { }
    """.asSmithyModel()
    private val model = OperationNormalizer.transform(baseModel)
    private val symbolProvider = serverTestSymbolProvider(model)

    @Test
    fun `generates combined error enums`() {
        val project = TestWorkspace.testProject(symbolProvider)
        project.withModule(RustModule.public("error")) { writer ->
            listOf("FooException", "ComplexError", "InvalidGreeting", "Deprecated").forEach {
                model.lookup<StructureShape>("error#$it").serverRenderWithModelBuilder(model, symbolProvider, writer)
            }
            val errors = listOf("FooException", "ComplexError", "InvalidGreeting").map { model.lookup<StructureShape>("error#$it") }
            val generator = ServerCombinedErrorGenerator(model, symbolProvider, symbolProvider.toSymbol(model.lookup("error#Greeting")), errors)
            generator.render(writer)

            writer.unitTest(
                name = "generates_combined_error_enums",
                test = """
                    let variant = InvalidGreeting { message: String::from("an error") };
                    assert_eq!(format!("{}", variant), "InvalidGreeting: an error");
                    assert_eq!(variant.message(), "an error");
                    assert_eq!(
                        variant.retryable_error_kind(),
                        aws_smithy_types::retry::ErrorKind::ClientError
                    );

                    let error = GreetingError::InvalidGreeting(variant);

                    // Generate is_xyz methods for errors.
                    assert_eq!(error.is_invalid_greeting(), true);
                    assert_eq!(error.is_complex_error(), false);

                    // Indicate the original name in the display output.
                    let error = FooException::builder().build();
                    assert_eq!(format!("{}", error), "FooException");

                    let error = Deprecated::builder().build();
                    assert_eq!(error.to_string(), "Deprecated");
                """,
            )

            writer.unitTest(
                name = "generates_converters_into_combined_error_enums",
                test = """
                    let variant = InvalidGreeting { message: String::from("an error") };
                    let error: GreetingError = variant.into();
                """,
            )

            project.compileAndTest()
        }
    }
}
