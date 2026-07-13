package dev.yawndoctor.rules

import dev.yawndoctor.ast.TransactionContext
import dev.yawndoctor.ast.calleeName
import dev.yawndoctor.ast.detectTransactionContext
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

class ExternalCallInsideTransactionRule(config: Config) : Rule(config) {

    override val issue = Issue(
        "ExternalCallInsideTransaction",
        Severity.Warning,
        "A configured external I/O call appears inside a database transaction.",
        Debt.FIVE_MINS,
    )

    private val transactionAnnotations: Set<String> by lazy {
        valueOrDefault("transactionAnnotations", listOf("Transactional")).toSet()
    }

    private val transactionFunctions: Set<String> by lazy {
        valueOrDefault("transactionFunctions", listOf("transaction", "inTransaction", "withTransaction", "open")).toSet()
    }

    private val suspiciousReceiverPatterns: Set<Regex> by lazy {
        valueOrDefault("suspiciousReceiverPatterns", listOf(".*Client", ".*Publisher", ".*Producer", "httpClient"))
            .map { it.toRegex() }.toSet()
    }

    private val suspiciousMethodNames: Set<String> by lazy {
        valueOrDefault("suspiciousMethodNames", listOf("send", "publish", "post", "execute", "reserve", "notify", "sleep")).toSet()
    }

    private val queryConstructors: Set<String> by lazy {
        valueOrDefault("queryConstructors", listOf("query", "createYawnCriteria", "createCriteria")).toSet()
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (!isSuspiciousCall(expression)) return

        if (expression.hasQueryConstructorInChain(queryConstructors)) return

        val context = detectTransactionContext(expression, transactionAnnotations, transactionFunctions) ?: return

        val callDescription = buildCallDescription(expression)

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[YAWN003][HIGH] External call `${callDescription}()` occurs " +
                    "inside a transaction (${describeContext(context)}). " +
                    "External I/O may extend lock duration and create " +
                    "ambiguous partial-failure behavior.",
            ),
        )
    }

    private fun isSuspiciousCall(expression: KtCallExpression): Boolean {
        val methodName = expression.calleeName() ?: return false
        if (methodName !in suspiciousMethodNames) return false

        if (isCallOnSuspiciousReceiver(expression)) return true

        return false
    }

    private fun isCallOnSuspiciousReceiver(expression: KtCallExpression): Boolean {
        val receiverText = extractReceiverText(expression) ?: return false
        return suspiciousReceiverPatterns.any { it.matches(receiverText) }
    }

    private fun extractReceiverText(expression: KtCallExpression): String? {
        val parent = expression.parent
        if (parent !is KtDotQualifiedExpression) return null
        if (parent.selectorExpression !== expression) return null
        val receiver = parent.receiverExpression
        return receiver.text
    }

    private fun buildCallDescription(expression: KtCallExpression): String {
        val methodName = expression.calleeName() ?: "<unknown>"
        val receiverText = extractReceiverText(expression)
        return if (receiverText != null) "$receiverText.$methodName" else methodName
    }

    private fun describeContext(context: TransactionContext): String = when (context) {
        is TransactionContext.TransactionAnnotation ->
            "@${context.annotationName} on ${context.function.name}"
        is TransactionContext.TransactionLambda ->
            "${context.functionName} {} lambda"
    }
}
