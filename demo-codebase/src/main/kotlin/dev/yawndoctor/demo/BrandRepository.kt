package dev.yawndoctor.demo

class BrandRepository(private val session: Session) {

    /** Materialized count: hydrates all entities, then counts in memory */
    fun countOrdersForBrand(brandId: Long): Int {
        return session.query(OrderTable).byId(brandId).list().size
    }

    /** Safe alternative: uses a database-level count projection */
    fun safeCountOrdersForBrand(brandId: Long): Long {
        return session.query(OrderTable).byId(brandId).countProjection()
    }
}
