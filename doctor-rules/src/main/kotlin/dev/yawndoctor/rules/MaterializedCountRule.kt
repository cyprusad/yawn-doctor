package dev.yawndoctor.rules

import dev.yawndoctor.ast.calleeName
import dev.yawndoctor.ast.hasQueryConstructorInChain
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class MaterializedCountRule(config: Config) : Rule(config) {

    override val issue = Issue(
        "MaterializedCount",
        Severity.Warning,
        "A recognized ORM query result is materialized before being counted.",
        Debt.FIVE_MINS,
    )

    private val queryConstructors: Set<String> by lazy {
        valueOrDefault("queryConstructors", listOf("query", "createYawnCriteria", "createCriteria")).toSet()
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        val selector = expression.selectorExpression ?: return

        val isSizeOnList = selector is KtNameReferenceExpression && selector.text == "size"
        val isCountOnList = selector is KtCallExpression && selector.calleeName() == "count"

        if (!isSizeOnList && !isCountOnList) return

        // The receiver of the outer DQE should itself be a DQE whose
        // selector is a call to `list()` — confirming the .list() step.
        val receiver = expression.receiverExpression
        if (receiver !is KtDotQualifiedExpression) return
        val innerSelector = receiver.selectorExpression
        if (innerSelector !is KtCallExpression) return
        if (innerSelector.calleeName() != "list") return

        // Verify that a query constructor exists in the receiver chain
        // starting from the `list()` call.
        if (!innerSelector.hasQueryConstructorInChain(queryConstructors)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(selector),
                message = "[YAWN002][HIGH] Query results are materialized before counting. " +
                    "Use a database-level count projection or dedicated count query.",
            ),
        )
    }
}
