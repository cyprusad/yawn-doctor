package dev.yawndoctor.rules

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalCallInsideTransactionRuleTest {

    private val dsl = """
        annotation class Transactional

        class ShippingClient {
            fun reserve(id: Long) = Unit
            fun notify(id: Long) = Unit
        }
        class EventPublisher {
            fun publish(event: Any) = Unit
        }
        class SimplePublisher {
            fun publish(event: Any) = Unit
        }
        class Query<T> {
            fun list(): List<T> = emptyList()
            fun count(): Long = 0
        }
        class CriteriaScope<T> {
            fun <V> addEq(column: Column<V>, value: V) = Unit
        }
        class Column<T>
        class Table<T>
        class Session {
            fun <T> createYawnCriteria(
                table: Table<T>,
                block: CriteriaScope<T>.() -> Unit,
            ): Query<T> = null!!
        }
        fun transaction(lambda: () -> Unit) {
            lambda()
        }
        fun <T> open(lambda: () -> T): T = null!!
        val shippingClient = ShippingClient()
        val eventPublisher = EventPublisher()
        val simplePublisher = SimplePublisher()
        val session = Session()
        val someTable = Table<String>()
        fun send(message: String) = Unit
    """.trimIndent()

    @Test
    fun `reports a client call inside an annotated function`() {
        val code = """
            $dsl
            @Transactional
            fun fulfill() {
                shippingClient.reserve(1L)
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a publisher call inside transaction lambda`() {
        val code = """
            $dsl
            fun fulfill() {
                transaction {
                    eventPublisher.publish("test")
                }
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports an external call inside open lambda`() {
        val code = """
            $dsl
            fun fulfill() {
                open {
                    shippingClient.reserve(1L)
                }
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report the same call outside a transaction`() {
        val code = """
            $dsl
            fun fulfill() {
                shippingClient.reserve(1L)
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a query call inside a transaction`() {
        val code = """
            $dsl
            @Transactional
            fun fulfill() {
                val items = session.createYawnCriteria(someTable) { }.list()
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a local helper named send`() {
        val code = """
            $dsl
            @Transactional
            fun fulfill() {
                send("hello")
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `known limitation programmatic transaction`() {
        val code = """
            $dsl
            class TransactionManager {
                fun begin() = Unit
                fun commit() = Unit
                fun rollback() = Unit
            }
            fun fulfill(manager: TransactionManager) {
                manager.begin()
                shippingClient.reserve(1L)
                manager.commit()
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `known limitation local class with suspicious name outside transaction`() {
        val code = """
            $dsl
            class RemoteClient {
                fun send(msg: String) = Unit
            }
            fun test() {
                RemoteClient().send("hello")
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured transaction annotations`() {
        val code = """
            $dsl
            @Transactional
            fun fulfill() {
                shippingClient.reserve(1L)
            }
        """.trimIndent()
        val config = TestConfig("transactionAnnotations" to listOf("OtherAnnotation"))
        val findings = ExternalCallInsideTransactionRule(config).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured transaction functions`() {
        val code = """
            $dsl
            fun fulfill() {
                transaction {
                    shippingClient.reserve(1L)
                }
            }
        """.trimIndent()
        val config = TestConfig("transactionFunctions" to listOf("customTx"))
        val findings = ExternalCallInsideTransactionRule(config).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects configured receiver regexes`() {
        val code = """
            $dsl
            @Transactional
            fun fulfill() {
                simplePublisher.publish("event")
            }
        """.trimIndent()
        val config = TestConfig(
            "suspiciousReceiverPatterns" to listOf(".*Client", ".*Producer"),
        )
        val findings = ExternalCallInsideTransactionRule(config).compileAndLint(code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports correct id and message`() {
        val code = """
            $dsl
            @Transactional
            fun fulfill() {
                shippingClient.reserve(1L)
            }
        """.trimIndent()
        val findings = ExternalCallInsideTransactionRule(TestConfig()).compileAndLint(code)
        assertEquals(1, findings.size)
        assertEquals("ExternalCallInsideTransaction", findings.first().id)
        assertTrue(findings.first().message.contains("YAWN003"))
        assertTrue(findings.first().message.contains("shippingClient.reserve"))
    }
}
