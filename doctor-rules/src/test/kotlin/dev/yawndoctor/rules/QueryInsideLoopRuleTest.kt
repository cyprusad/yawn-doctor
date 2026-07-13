package dev.yawndoctor.rules

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryInsideLoopRuleTest {

    private val dsl = """
        class Table<T>
        class Column<T>
        class Query<T> {
            fun list(): List<T> = emptyList()
            fun first(): T? = null
            fun single(): T = null!!
            fun count(): Long = 0
        }
        class CriteriaScope<T> {
            fun <V> addEq(column: Column<V>, value: V) = Unit
        }
        class Session {
            fun <T> createYawnCriteria(
                table: Table<T>,
                block: CriteriaScope<T>.() -> Unit,
            ): Query<T> = null!!
        }
        val session = Session()
        val someTable = Table<String>()
    """.trimIndent()

    @Test
    fun `reports list inside forEach`() {
        val code = """
            $dsl
            fun test() {
                listOf(1, 2, 3).forEach {
                    session.createYawnCriteria(someTable) { }.list()
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
        assertEquals("QueryInsideLoop", findings.first().id)
        assertTrue(findings.first().message.contains("list"))
        assertTrue(findings.first().message.contains("forEach"))
    }

    @Test
    fun `reports list inside for loop`() {
        val code = """
            $dsl
            fun test() {
                val items = listOf(1, 2, 3)
                for (i in items) {
                    session.createYawnCriteria(someTable) { }.list()
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports list inside map`() {
        val code = """
            $dsl
            fun test() {
                listOf(1, 2, 3).map {
                    session.createYawnCriteria(someTable) { }.list()
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("map"))
    }

    @Test
    fun `reports list inside while loop`() {
        val code = """
            $dsl
            fun test() {
                var i = 0
                while (i < 3) {
                    session.createYawnCriteria(someTable) { }.list()
                    i++
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report query executed before loop`() {
        val code = """
            $dsl
            fun test() {
                val result = session.createYawnCriteria(someTable) { }.list()
                listOf(1, 2, 3).forEach {
                    println(it)
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report listOf inside a loop`() {
        val code = """
            $dsl
            fun test() {
                listOf(1, 2, 3).forEach {
                    val x = listOf(1, 2, 3)
                    println(x)
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report normal collection calls`() {
        val code = """
            $dsl
            fun test() {
                listOf(1, 2, 3).forEach {
                    val x = listOf(1, 2, 3).size
                    println(x)
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report unrelated list with no query origin`() {
        val code = """
            $dsl
            class Foo {
                fun list(): List<Int> = emptyList()
            }
            fun test() {
                listOf(1, 2, 3).forEach {
                    Foo().list()
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured terminal names`() {
        val code = """
            $dsl
            fun test() {
                listOf(1, 2, 3).forEach {
                    session.createYawnCriteria(someTable) { }.first()
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("first"))
    }

    @Test
    fun `does not report count when query is before loop`() {
        val code = """
            $dsl
            fun test() {
                val count = session.createYawnCriteria(someTable) { }.count()
                listOf(1, 2, 3).forEach {
                    println(it)
                }
            }
        """.trimIndent()
        val findings = QueryInsideLoopRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }
}
