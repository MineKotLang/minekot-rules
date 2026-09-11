package org.minekot.rules

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.*

/** Static security and idempotence contract for rules release workflow. */
class ReleaseWorkflowContractTest {
    /** Workflow stays master-only, least-privilege, immutable, signed, and idempotent. */
    @Test
    fun `release workflow preserves production invariants`() {
        val workflow = Path(System.getProperty("minekot.rootDir"), ".github/workflows/release.yml").readText()
        val actionReferences = Regex("(?m)^\\s*uses:\\s*[^@\\s]+@([^\\s]+)$")
            .findAll(workflow)
            .map { match -> match.groupValues[1] }
            .toList()

        assertTrue(Regex("branches:\\n\\s+- master").containsMatchIn(workflow))
        assertFalse(workflow.contains("pull_request:"))
        assertTrue(workflow.contains("cancel-in-progress: true"))
        assertTrue(Regex("contents: write\\n\\s+id-token: write").containsMatchIn(workflow))
        assertTrue(workflow.contains("test \"${'$'}{GITHUB_SHA}\" = \"${'$'}(git rev-parse origin/master)\""))
        assertTrue(actionReferences.isNotEmpty())
        assertTrue(actionReferences.all { reference -> reference.matches(Regex("[0-9a-f]{40}")) })
        assertTrue(workflow.contains("cosign verify-blob"))
        assertTrue(workflow.contains("MineKotLang/minekot-rules/.github/workflows/release.yml@refs/heads/master"))
        assertTrue(workflow.contains("test \"${'$'}(find existing -maxdepth 1 -type f | wc -l)\" -eq 4"))
        assertTrue(workflow.contains("cmp existing/SHA256SUMS final/SHA256SUMS"))
        assertTrue(workflow.indexOf("Publish immutable release") < workflow.indexOf("Update signed stable channel"))
    }
}
