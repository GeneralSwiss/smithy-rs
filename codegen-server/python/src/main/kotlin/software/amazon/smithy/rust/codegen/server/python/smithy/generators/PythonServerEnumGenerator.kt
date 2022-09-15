/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.python.smithy.generators

import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.rust.codegen.client.rustlang.Attribute
import software.amazon.smithy.rust.codegen.client.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.client.rustlang.Writable
import software.amazon.smithy.rust.codegen.client.rustlang.asType
import software.amazon.smithy.rust.codegen.client.rustlang.rust
import software.amazon.smithy.rust.codegen.client.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.client.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.client.rustlang.writable
import software.amazon.smithy.rust.codegen.client.smithy.ServerCodegenContext
import software.amazon.smithy.rust.codegen.core.util.dq
import software.amazon.smithy.rust.codegen.server.python.smithy.PythonServerCargoDependency
import software.amazon.smithy.rust.codegen.server.smithy.generators.ServerEnumGenerator

/**
 * To share enums defined in Rust with Python, `pyo3` provides the `PyClass` trait.
 * This class generates enums definitions, implements the `PyClass` trait and adds
 * some utility functions like `__str__()` and `__repr__()`.
 */
class PythonServerEnumGenerator(
    codegenContext: ServerCodegenContext,
    private val writer: RustWriter,
    shape: StringShape,
) : ServerEnumGenerator(codegenContext, writer, shape) {

    private val pyo3Symbols = listOf(PythonServerCargoDependency.PyO3.asType())

    override fun render() {
        renderPyClass()
        super.render()
        renderPyO3Methods()
    }

    private fun renderPyClass() {
        Attribute.Custom("pyo3::pyclass", symbols = pyo3Symbols).render(writer)
    }

    private fun renderPyO3Methods() {
        Attribute.Custom("pyo3::pymethods", symbols = pyo3Symbols).render(writer)
        writer.rustTemplate(
            """
            impl $enumName {
                #{name_method:W}
                ##[getter]
                pub fn value(&self) -> &str {
                    self.as_str()
                }
                fn __repr__(&self) -> String  {
                    self.as_str().to_owned()
                }
                fn __str__(&self) -> String {
                    self.as_str().to_owned()
                }
            }
            """,
            "name_method" to renderPyEnumName(),
        )
    }

    private fun renderPyEnumName(): Writable =
        writable {
            rustBlock(
                """
                ##[getter]
                pub fn name(&self) -> &str
                """,
            ) {
                rustBlock("match self") {
                    sortedMembers.forEach { member ->
                        val memberName = member.name()?.name
                        rust("""$enumName::$memberName => ${memberName?.dq()},""")
                    }
                }
            }
        }
}
