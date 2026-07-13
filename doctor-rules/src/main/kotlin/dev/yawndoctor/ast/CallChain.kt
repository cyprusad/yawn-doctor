package dev.yawndoctor.ast

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

fun KtCallExpression.calleeName(): String? {
    val callee = calleeExpression ?: return null
    return when (callee) {
        is KtDotQualifiedExpression ->
            (callee.selectorExpression as? KtNameReferenceExpression)?.text
        is KtNameReferenceExpression -> callee.text
        else -> callee.text
    }
}

fun KtCallExpression.isCallTo(name: String): Boolean =
    calleeName() == name

/**
 * Walks the receiver chain of a call expression and yields every
 * [KtCallExpression] found along the way — including calls nested inside
 * [KtDotQualifiedExpression] selectors.
 *
 * Example — for `session.query(UserTable).byId(id).list()`, the PSI tree is:
 *
 *   DQE-A (session.query(UserTable).byId(id).list())
 *   ├── receiver: DQE-B (session.query(UserTable).byId(id))
 *   │   ├── receiver: DQE-C (session.query(UserTable))
 *   │   │   ├── receiver: session (KtNameRef)
 *   │   │   └── selector: query(UserTable) (KtCallExpr)
 *   │   └── selector: byId(id) (KtCallExpr)
 *   └── selector: list() (KtCallExpr)
 *
 * Starting from `list()`, this walks UP through parent DQEs and yields
 * every call-expression selector found along the way: [list(), byId(id), query(UserTable)].
 */
fun walkReceiverChain(expression: KtCallExpression): Sequence<KtCallExpression> = sequence {
    yield(expression)

    var current: KtExpression? = expression
    while (current != null) {
        // Step up: if this node is the selector of a dot-qualified expression,
        // move to its receiver (the left-hand side of the dot).
        val parent = current.parent
        current = when {
            parent is KtDotQualifiedExpression && parent.selectorExpression == current -> {
                parent.receiverExpression
            }
            else -> null
        }

        // The receiver of a dot may itself be a dot-qualified expression
        // (e.g. `session.query(…).byId(…)` is the receiver of `.list()`).
        // Drain the chain of nested DQEs, yielding every call-expression selector.
        while (current is KtDotQualifiedExpression) {
            current.selectorExpression?.let {
                if (it is KtCallExpression) yield(it)
            }
            current = current.receiverExpression
        }
    }
}
