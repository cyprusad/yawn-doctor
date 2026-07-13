package dev.yawndoctor.rules

import dev.yawndoctor.ast.calleeName
import dev.yawndoctor.ast.findQueryConstructorCall
import dev.yawndoctor.ast.hasJoinCallInLambda
import dev.yawndoctor.ast.hasQueryConstructorInChain
import dev.yawndoctor.ast.isFollowedByDistinct
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

class CollectionJoinWithoutDistinctRule(config: Config) : Rule(config) {

    override val issue = Issue(
        "CollectionJoinWithoutDistinct",
        Severity.Warning,
        "An entity query joins a collection association without deduplicating results.",
        Debt.FIVE_MINS,
    )

    private val queryConstructors: Set<String> by lazy {
        valueOrDefault("queryConstructors", listOf("createYawnCriteria", "createCriteria", "query")).toSet()
    }

    private val queryTerminals: Set<String> by lazy {
        valueOrDefault("queryTerminals", listOf("list")).toSet()
    }

    private val joinFunctionNames: Set<String> by lazy {
        valueOrDefault("joinFunctionNames", listOf("join", "createAlias")).toSet()
    }

    private val distinctMethods: Set<String> by lazy {
        valueOrDefault("distinctMethods", listOf("distinctBy", "distinctRootEntity")).toSet()
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeName() ?: return
        if (callee !in queryTerminals) return
        if (!expression.hasQueryConstructorInChain(queryConstructors)) return

        // Check that the query constructor has a lambda with join() calls
        val constructorCall = expression.findQueryConstructorCall(queryConstructors) ?: return
        if (!constructorCall.hasJoinCallInLambda(joinFunctionNames)) return

        // If list() is followed by distinctBy or distinctRootEntity, no problem
        if (expression.isFollowedByDistinct(distinctMethods)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[YAWN004][HIGH] Entity query joins a collection and calls `${callee}()` " +
                    "without deduplication. Collection joins can return duplicate root entities. " +
                    "Add `.distinctBy { it.id }` or, when available, `.distinctRootEntity()`.",
            ),
        )
    }
}
