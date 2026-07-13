package dev.yawndoctor.demo

class Table<T>(val name: String)

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

class Session {
    fun <T> query(table: Table<T>): Query<T> = Query()
}
