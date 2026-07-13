@file:Suppress("FunctionOnlyReturningConstant", "UnusedParameter", "UnusedPrivateProperty")

package dev.yawndoctor.demo

val reviewsCol = Column<String>()
val titleCol = Column<String>()

class BookTable : Table<Book>("books")

data class Book(val id: Long, val title: String)

class BookRepository(private val session: Session) {

    // YAWN004: joins collection, calls .list() without dedup
    fun loadBooksWithReviews(token: String): List<Book> {
        return session.createYawnCriteria(BookTable()) {
            join(reviewsCol)
        }.list()
    }

    // OK: no join in lambda
    fun loadBooksWithoutJoin(token: String): List<Book> {
        return session.createYawnCriteria(BookTable()) {
            addEq(titleCol, token)
        }.list()
    }

    // OK: distinctBy after list()
    fun loadBooksWithDedup(token: String): List<Book> {
        return session.createYawnCriteria(BookTable()) {
            join(reviewsCol)
        }.list().distinctBy { it.id }
    }
}
