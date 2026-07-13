package dev.yawndoctor.rules

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionJoinWithoutDistinctRuleTest {

    private val dsl = """
        class Table<T>
        class Column<T>
        class Query<T> {
            fun list(): List<T> = emptyList()
        }
        class CriteriaScope<T> {
            fun join(column: Any, joinType: Any = "INNER") = Unit
            fun createAlias(path: String, alias: String) = Unit
        }
        class Session {
            fun <T> createYawnCriteria(
                table: Table<T>,
                block: CriteriaScope<T>.() -> Unit,
            ): Query<T> = null!!
            fun <T> createCriteria(
                table: Table<T>,
                block: CriteriaScope<T>.() -> Unit,
            ): Query<T> = null!!
            fun <T> query(table: Table<T>): Query<T> = Query()
        }
        val session = Session()
        val someTable = Table<String>()
        val someColumn = Column<String>()
    """.trimIndent()

    @Test
    fun `reports join in createYawnCriteria without distinct`() {
        val code = """
            $dsl
            fun test() {
                session.createYawnCriteria(someTable) {
                    join(someColumn, joinType = "LEFT_OUTER_JOIN")
                    addEq(1)
                }.list()
            }
            fun CriteriaScope<*>.addEq(v: Any) = Unit
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
        assertEquals("CollectionJoinWithoutDistinct", findings.first().id)
        assertTrue(findings.first().message.contains("list"))
    }

    @Test
    fun `reports createCriteria with join without distinct`() {
        val code = """
            $dsl
            fun test() {
                session.createCriteria(someTable) {
                    join(someColumn)
                }.list()
            }
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports createYawnCriteria with createAlias without distinct`() {
        val code = """
            $dsl
            fun test() {
                session.createYawnCriteria(someTable) {
                    createAlias("reviews", "reviews")
                }.list()
            }
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report when distinctBy follows list`() {
        val code = """
            $dsl
            fun test() {
                session.createYawnCriteria(someTable) {
                    join(someColumn)
                }.list().distinctBy { it }
            }
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when query constructor has no join`() {
        val code = """
            $dsl
            fun test() {
                session.createYawnCriteria(someTable) { }.list()
            }
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report plain query without lambda`() {
        val code = """
            $dsl
            fun test() {
                session.query(someTable).list()
            }
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report non-list call`() {
        val code = """
            $dsl
            class Fake {
                fun join(vararg args: Any) = Unit
                fun list() = emptyList<Int>()
            }
            fun test() {
                Fake().join(1, 2, 3).list()
            }
        """.trimIndent()
        val findings = CollectionJoinWithoutDistinctRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured join names`() {
        val code = """
            $dsl
            fun test() {
                session.createYawnCriteria(someTable) {
                    join(someColumn)
                }.list()
            }
        """.trimIndent()
        // Only "customJoin" is configured, not "join"
        val config = TestConfig("joinFunctionNames" to listOf("customJoin"))
        val findings = CollectionJoinWithoutDistinctRule(config).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured distinct methods`() {
        val code = """
            $dsl
            fun test() {
                session.createYawnCriteria(someTable) {
                    join(someColumn)
                }.list().distinctBy { it }
            }
        """.trimIndent()
        // Only "customDistinct" is configured, not "distinctBy"
        val config = TestConfig("distinctMethods" to listOf("customDistinct"))
        val findings = CollectionJoinWithoutDistinctRule(config).compileAndLint(code)
        assertEquals(1, findings.size)
    }
}
