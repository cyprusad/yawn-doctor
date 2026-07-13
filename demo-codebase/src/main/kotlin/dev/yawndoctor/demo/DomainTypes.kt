// Stub types for demo purposes — @Suppress avoids noise from built-in detekt
// rules (FunctionOnlyReturningConstant, UnusedParameter) that aren't relevant
// to the Yawn Doctor rules being demonstrated.

@file:Suppress("FunctionOnlyReturningConstant", "UnusedParameter", "UnsafeCallOnNullableType")

package dev.yawndoctor.demo

open class Table<T>(val name: String)

data class Order(val id: Long, val brandId: Long)

class OrderTable : Table<Order>("orders")

class UserTable : Table<User>("users")

data class User(val id: Long, val name: String)

class Query<T> {
    fun <V> byId(id: V): Query<T> = this
    fun list(): List<T> = emptyList()
    fun first(): T? = null
    fun single(): T = null!!
    fun count(): Long = 0
    fun countProjection(): Long = 0
}

class Column<T>

class CriteriaScope<T> {
    fun join(column: Any, joinType: Any = "INNER") = Unit
    fun <V> addEq(column: Column<V>, value: V) = Unit
}

class Session {
    fun <T> query(table: Table<T>): Query<T> = Query()
    fun <T> createYawnCriteria(
        table: Table<T>,
        block: CriteriaScope<T>.() -> Unit,
    ): Query<T> = null!!
}

annotation class Transactional

class ShippingClient {
    fun reserve(id: Long) = Unit
    fun notify(id: Long) = Unit
}

class EventPublisher {
    fun publish(event: Any) = Unit
}

class YawnTestSession(val hibernateSession: Any)

class YawnTestTransactor {
    fun <T> open(lambda: (YawnTestSession) -> T): T = null!!
}
