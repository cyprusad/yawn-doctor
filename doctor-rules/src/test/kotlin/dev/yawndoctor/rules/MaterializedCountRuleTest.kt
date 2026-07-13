package dev.yawndoctor.rules

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaterializedCountRuleTest {

    private val dsl = """
        class Table<T>
        class Query<T> {
            fun list(): List<T> = emptyList()
            fun countProjection(): Long = 0
        }
        class CriteriaScope<T> {
            fun <V> addEq(column: Column<V>, value: V) = Unit
        }
        class Column<T>
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
    fun `reports list dot size`() {
        val code = """
            $dsl
            fun test() {
                val count = session.createYawnCriteria(someTable) { }.list().size
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports list dot count`() {
        val code = """
            $dsl
            fun test() {
                val count = session.createYawnCriteria(someTable) { }.list().count()
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports multiline call chain`() {
        val code = """
            $dsl
            fun test() {
                val count = session
                    .createYawnCriteria(someTable) { }
                    .list()
                    .size
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report plain list size`() {
        val code = """
            $dsl
            fun test() {
                val items = listOf(1, 2, 3)
                val count = items.size
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report plain list count`() {
        val code = """
            $dsl
            fun test() {
                val items = listOf(1, 2, 3)
                val count = items.count()
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report size when query origin is no longer visible`() {
        val code = """
            $dsl
            fun test() {
                val results = session.createYawnCriteria(someTable) { }.list()
                val count = results.size
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report dedicated count projection`() {
        val code = """
            $dsl
            fun test() {
                val count = session.createYawnCriteria(someTable) { }.countProjection()
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports correct source location`() {
        val code = """
            $dsl
            fun test() {
                val count = session.createYawnCriteria(someTable) { }.list().size
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
        assertEquals("MaterializedCount", findings.first().id)
        assertTrue(findings.first().message.contains("YAWN002"))
    }

    @Test
    fun `known limitation list dot isNotEmpty`() {
        val code = """
            $dsl
            fun test() {
                val present = session.createYawnCriteria(someTable) { }.list().isNotEmpty()
            }
        """.trimIndent()
        val findings = MaterializedCountRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured query constructors`() {
        val code = """
            $dsl
            fun test() {
                val count = session.createYawnCriteria(someTable) { }.list().size
            }
        """.trimIndent()
        val config = TestConfig("queryConstructors" to listOf("nonexistent"))
        val findings = MaterializedCountRule(config).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }
}
