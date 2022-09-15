/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.server.smithy.generators

import org.junit.jupiter.api.Test
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.client.rustlang.RustModule
import software.amazon.smithy.rust.codegen.server.smithy.testutil.serverRenderWithModelBuilder
import software.amazon.smithy.rust.codegen.server.smithy.testutil.serverTestCodegenContext
import software.amazon.smithy.rust.codegen.client.smithy.ModelsModule
import software.amazon.smithy.rust.codegen.client.testutil.TestWorkspace
import software.amazon.smithy.rust.codegen.client.testutil.asSmithyModel
import software.amazon.smithy.rust.codegen.client.testutil.compileAndTest
import software.amazon.smithy.rust.codegen.client.testutil.unitTest
import software.amazon.smithy.rust.codegen.client.util.lookup

class UnconstrainedMapGeneratorTest {
    @Test
    fun `it should generate unconstrained maps`() {
        val model =
            """
            namespace test

            service TestService {
                version: "123",
                operations: [TestOperation]
            }
            
            operation TestOperation {
                input: TestInputOutput,
                output: TestInputOutput,
            }
            
            structure TestInputOutput {
                map: MapA
            }
            
            map MapA {
                key: String,
                value: MapB
            }
            
            map MapB {
                key: String,
                value: StructureC
            }
            
            structure StructureC {
                @required
                int: Integer,
                
                @required
                string: String
            }
            """.asSmithyModel()
        val codegenContext = serverTestCodegenContext(model)
        val symbolProvider = codegenContext.symbolProvider

        val mapA = model.lookup<MapShape>("test#MapA")
        val mapB = model.lookup<MapShape>("test#MapB")

        val project = TestWorkspace.testProject(symbolProvider)

        project.withModule(RustModule.public("model")) { writer ->
            model.lookup<StructureShape>("test#StructureC").serverRenderWithModelBuilder(model, symbolProvider, writer)
        }

        project.withModule(RustModule.private("constrained")) { writer ->
            listOf(mapA, mapB).forEach {
                PubCrateConstrainedMapGenerator(codegenContext, writer, it).render()
            }
        }
        project.withModule(RustModule.private("unconstrained")) { unconstrainedModuleWriter ->
            project.withModule(ModelsModule) { modelsModuleWriter ->
                listOf(mapA, mapB).forEach {
                    UnconstrainedMapGenerator(codegenContext, unconstrainedModuleWriter, it).render()

                    MapConstraintViolationGenerator(codegenContext, modelsModuleWriter, it).render()
                }

                unconstrainedModuleWriter.unitTest(
                    name = "map_a_unconstrained_fail_to_constrain_with_some_error",
                    test = """
                        let c_builder1 = crate::model::StructureC::builder().int(69);
                        let c_builder2 = crate::model::StructureC::builder().string(String::from("david"));
                        let map_b_unconstrained = map_b_unconstrained::MapBUnconstrained(
                            std::collections::HashMap::from([
                                (String::from("KeyB1"), c_builder1),
                                (String::from("KeyB2"), c_builder2),
                            ])
                        );
                        let map_a_unconstrained = map_a_unconstrained::MapAUnconstrained(
                            std::collections::HashMap::from([
                                (String::from("KeyA"), map_b_unconstrained),
                            ])
                        );

                        // Any of these two errors could be returned; it depends on the order in which the maps are visited.
                        let missing_string_expected_err =
                            crate::model::map_a::ConstraintViolation::Value(crate::model::map_b::ConstraintViolation::Value(
                                crate::model::structure_c::ConstraintViolation::MissingString,
                            ));
                        let missing_int_expected_err =
                            crate::model::map_a::ConstraintViolation::Value(crate::model::map_b::ConstraintViolation::Value(
                                crate::model::structure_c::ConstraintViolation::MissingInt,
                            ));
                            
                        let actual_err = crate::constrained::map_a_constrained::MapAConstrained::try_from(map_a_unconstrained).unwrap_err();

                        assert!(actual_err == missing_string_expected_err || actual_err == missing_int_expected_err);
                        """,
                )

                unconstrainedModuleWriter.unitTest(
                    name = "map_a_unconstrained_succeed_to_constrain",
                    test = """
                    let c_builder = crate::model::StructureC::builder().int(69).string(String::from("david"));
                    let map_b_unconstrained = map_b_unconstrained::MapBUnconstrained(
                        std::collections::HashMap::from([
                            (String::from("KeyB"), c_builder),
                        ])
                    );
                    let map_a_unconstrained = map_a_unconstrained::MapAUnconstrained(
                        std::collections::HashMap::from([
                            (String::from("KeyA"), map_b_unconstrained),
                        ])
                    );

                    let expected = std::collections::HashMap::from([
                        (String::from("KeyA"), std::collections::HashMap::from([
                            (String::from("KeyB"), crate::model::StructureC {
                                int: 69,
                                string: String::from("david")
                            }),
                        ]))
                    ]);

                    assert_eq!(
                        expected,
                        crate::constrained::map_a_constrained::MapAConstrained::try_from(map_a_unconstrained).unwrap().into()
                    );
                    """,
                )

                unconstrainedModuleWriter.unitTest(
                    name = "map_a_unconstrained_converts_into_constrained",
                    test = """
                    let c_builder = crate::model::StructureC::builder();
                    let map_b_unconstrained = map_b_unconstrained::MapBUnconstrained(
                        std::collections::HashMap::from([
                            (String::from("KeyB"), c_builder),
                        ])
                    );
                    let map_a_unconstrained = map_a_unconstrained::MapAUnconstrained(
                        std::collections::HashMap::from([
                            (String::from("KeyA"), map_b_unconstrained),
                        ])
                    );

                    let _map_a: crate::constrained::MaybeConstrained<crate::constrained::map_a_constrained::MapAConstrained> = map_a_unconstrained.into();
                    """,
                )

                project.compileAndTest()
            }
        }
    }
}
