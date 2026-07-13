package dev.yawndoctor.ast

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

sealed class LoopContext {
    data class ForLoop(val expression: KtForExpression) : LoopContext()
    data class WhileLoop(val expression: KtWhileExpression) : LoopContext()
    data class IterationLambda(
        val lambda: KtLambdaExpression,
        val call: KtCallExpression,
        val functionName: String,
    ) : LoopContext()
}

/**
 * Walks up the PSI ancestor chain from [expression] and returns the
 * first loop construct found — either a `for`/`while` statement or a
 * lambda passed to an iteration function (e.g. forEach, map).
 *
 * Stops at function boundaries ([KtNamedFunction]) so that loops
 * in outer callers aren't falsely attributed to the expression.
 */
fun detectLoopContext(expression: KtExpression, iterationFunctions: Set<String>): LoopContext? {
    val ancestors = expression.parents.toList()

    for (ancestor in ancestors) {
        when (ancestor) {
            is KtForExpression -> return LoopContext.ForLoop(ancestor)
            is KtWhileExpression -> return LoopContext.WhileLoop(ancestor)
            is KtLambdaExpression -> {
                val parentCall = findParentIterationCall(ancestor)
                if (parentCall != null) {
                    val name = parentCall.calleeName()
                    if (name != null && name in iterationFunctions) {
                        return LoopContext.IterationLambda(ancestor, parentCall, name)
                    }
                }
            }
            is KtNamedFunction -> return null
        }
    }
    return null
}

private fun findParentIterationCall(lambda: KtLambdaExpression): KtCallExpression? {
    val lambdaArg = lambda.parent
    if (lambdaArg !is KtLambdaArgument) return null
    val call = lambdaArg.parent
    if (call !is KtCallExpression) return null
    return call
}

fun LoopContext.displayName(): String = when (this) {
    is LoopContext.ForLoop -> "for"
    is LoopContext.WhileLoop -> "while"
    is LoopContext.IterationLambda -> functionName
}
