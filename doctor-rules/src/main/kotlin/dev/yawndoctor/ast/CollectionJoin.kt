package dev.yawndoctor.ast

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral

/**
 * Returns true if any lambda argument of this call expression contains
 * a top-level call whose name matches one of [joinNames].
 *
 * Used to detect `createYawnCriteria(…) { … join(…) … }` patterns
 * where an entity query joins a collection association.
 */
fun KtCallExpression.hasJoinCallInLambda(joinNames: Set<String>): Boolean {
    return lambdaArguments.any { lambdaArg ->
        val literal = lambdaArg.getLambdaExpression() ?: return@any false
        val bodyExpression = literal.bodyExpression ?: return@any false
        val block = bodyExpression as? KtBlockExpression ?: return@any false
        block.statements.any { statement ->
            statement is KtCallExpression && statement.calleeName() in joinNames
        }
    }
}

/**
 * Walks the receiver chain from [this] call and returns the first
 * [KtCallExpression] whose callee name is in [constructors], or null.
 */
fun KtCallExpression.findQueryConstructorCall(constructors: Set<String>): KtCallExpression? {
    return walkReceiverChain(this).firstOrNull { element ->
        element.calleeName() in constructors
    }
}

/**
 * Checks whether this call expression is followed by a dedup or
 * distinct-root-entity call in the same chain.
 *
 * For `query.list().distinctBy { … }` the PSI tree is:
 *
 *   DQE-B (query.list().distinctBy { … })
 *   ├── receiver: DQE-A (query.list())
 *   │   ├── receiver: … (query)
 *   │   └── selector: list() (this)
 *   └── selector: distinctBy { … } (KtCallExpr)
 *
 * Walks from [this] up to DQE-A, then checks DQE-B's selector.
 */
fun KtCallExpression.isFollowedByDistinct(distinctNames: Set<String>): Boolean {
    val parentDqe = parent as? KtDotQualifiedExpression ?: return false
    val outerDqe = parentDqe.parent as? KtDotQualifiedExpression ?: return false
    val outerSelector = outerDqe.selectorExpression as? KtCallExpression ?: return false
    return outerSelector.calleeName() in distinctNames
}
