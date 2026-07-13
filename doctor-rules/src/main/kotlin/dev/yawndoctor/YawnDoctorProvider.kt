package dev.yawndoctor

import dev.yawndoctor.rules.QueryInsideLoopRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class YawnDoctorProvider : RuleSetProvider {

    override val ruleSetId: String = "YawnDoctor"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf(QueryInsideLoopRule(config)),
        )
    }
}
