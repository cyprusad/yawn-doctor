package dev.yawndoctor.ast

import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Walks this call's receiver chain and returns true if any call in the
 * chain matches one of the given ORM query constructors (e.g. "query",
 * "createYawnCriteria").  This tells us the call originates from a
 * database query rather than a plain Kotlin collection.
 */
fun KtCallExpression.hasQueryConstructorInChain(constructors: Set<String>): Boolean {
    return walkReceiverChain(this).any { element ->
        element.calleeName() in constructors
    }
}
