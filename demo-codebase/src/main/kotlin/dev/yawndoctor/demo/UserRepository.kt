package dev.yawndoctor.demo

class UserRepository(private val session: Session) {

    /** N+1: queries list() inside a forEach — one query per user */
    fun sendPromotionalEmailsToUsers(userIds: List<Long>) {
        userIds.forEach { id ->
            val user = session.query(UserTable).byId(id).list()
            println("Sent email to ${user.name}")
        }
    }

    /** N+1: queries list() inside a for loop */
    fun generateReport(userIds: List<Long>) {
        val users = userIds.map { id ->
            session.query(UserTable).byId(id).list()
        }
        println("Report: $users")
    }
}
