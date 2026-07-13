package dev.yawndoctor.ast

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parents

sealed class TransactionContext {
    data class TransactionAnnotation(
        val function: KtNamedFunction,
        val annotationName: String,
    ) : TransactionContext()

    data class TransactionLambda(
        val lambda: KtLambdaExpression,
        val call: KtCallExpression,
        val functionName: String,
    ) : TransactionContext()
}

/**
 * Walks up the PSI ancestor chain from [expression] and returns the
 * first transaction context found — either a function annotated with a
 * configured transaction annotation (e.g. @Transactional) or a lambda
 * passed to a configured transaction function (e.g. transaction, open).
 *
 * Stops at function boundaries ([KtNamedFunction]) that don't themselves
 * carry a transaction annotation, so calls in outer callers aren't
 * falsely attributed.
 */
fun detectTransactionContext(
    expression: KtExpression,
    transactionAnnotations: Set<String>,
    transactionFunctions: Set<String>,
): TransactionContext? {
    val ancestors = expression.parents.toList()

    for (ancestor in ancestors) {
        when (ancestor) {
            is KtLambdaExpression -> {
                val parentCall = findParentTransactionCall(ancestor)
                if (parentCall != null) {
                    val name = parentCall.calleeName()
                    if (name != null && name in transactionFunctions) {
                        return TransactionContext.TransactionLambda(ancestor, parentCall, name)
                    }
                }
            }
            is KtNamedFunction -> {
                val annotationName = findTransactionAnnotation(ancestor, transactionAnnotations)
                if (annotationName != null) {
                    return TransactionContext.TransactionAnnotation(ancestor, annotationName)
                }
                return null
            }
        }
    }
    return null
}

private fun findParentTransactionCall(lambda: KtLambdaExpression): KtCallExpression? {
    val lambdaArg = lambda.parent
    if (lambdaArg !is KtLambdaArgument) return null
    val call = lambdaArg.parent
    if (call !is KtCallExpression) return null
    return call
}

private fun findTransactionAnnotation(
    function: KtNamedFunction,
    transactionAnnotations: Set<String>,
): String? {
    val modifierList = function.modifierList ?: return null
    for (annotationEntry in modifierList.annotationEntries) {
        val shortName = annotationEntry.shortName?.asString() ?: continue
        if (shortName in transactionAnnotations) return shortName
    }
    return null
}
