package dev.yawndoctor.rules

import dev.yawndoctor.ast.calleeName
import dev.yawndoctor.ast.detectLoopContext
import dev.yawndoctor.ast.displayName
import dev.yawndoctor.ast.hasQueryConstructorInChain
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

class QueryInsideLoopRule(config: Config) : Rule(config) {

    override val issue = Issue(
        "QueryInsideLoop",
        Severity.Warning,
        "A recognized ORM query terminal executes inside an iteration construct.",
        Debt.FIVE_MINS,
    )

    private val queryConstructors: Set<String> by lazy {
        valueOrDefault("queryConstructors", listOf("query", "createYawnCriteria", "createCriteria")).toSet()
    }

    private val queryTerminals: Set<String> by lazy {
        valueOrDefault("queryTerminals", listOf("list", "first", "single", "count")).toSet()
    }

    private val iterationFunctions: Set<String> by lazy {
        valueOrDefault("iterationFunctions", listOf("forEach", "map", "flatMap", "associate", "onEach")).toSet()
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeName() ?: return
        if (callee !in queryTerminals) return
        if (!expression.hasQueryConstructorInChain(queryConstructors)) return

        val loopContext = detectLoopContext(expression, iterationFunctions) ?: return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[YAWN001][HIGH] Query terminal `${callee}()` executes inside `${loopContext.displayName()}`. " +
                    "Database round trips may grow with the number of input elements.",
            ),
        )
    }
}
