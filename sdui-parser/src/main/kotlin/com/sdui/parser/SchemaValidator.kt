package com.sdui.parser

import com.sdui.core.model.ActionSpec
import com.sdui.core.model.ActionTypes
import com.sdui.core.model.SduiNode
import com.sdui.core.model.SduiScreen

public enum class IssueSeverity { ERROR, WARNING }

public data class ValidationIssue(
    val severity: IssueSeverity,
    val path: String,
    val message: String,
)

public data class ValidationReport(
    val issues: List<ValidationIssue> = emptyList(),
) {
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == IssueSeverity.WARNING }
    public val hasErrors: Boolean get() = errors.isNotEmpty()
    public val isClean: Boolean get() = issues.isEmpty()

    public fun describe(): String =
        issues.joinToString("\n") { "[${it.severity}] ${it.path}: ${it.message}" }

    public companion object {
        public val Clean: ValidationReport = ValidationReport()
    }
}

/**
 * Structural checks that JSON deserialization alone cannot express: unknown
 * component and action types, duplicate ids, runaway nesting, dangling API
 * references, and `weight` used outside a flex container.
 *
 * Runs on the device before render (cheap: one tree walk) and on the dashboard
 * before publish, so authors see the same diagnostics the SDK would.
 */
public class SchemaValidator(
    private val maxDepth: Int = 64,
    private val maxNodes: Int = 5_000,
) {
    public fun validate(
        screen: SduiScreen,
        knownComponents: Set<String> = emptySet(),
        knownActions: Set<String> = emptySet(),
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        val seenIds = mutableMapOf<String, String>()
        var nodeCount = 0

        if (screen.id.isBlank()) {
            issues += ValidationIssue(IssueSeverity.ERROR, "screen.id", "Screen id must not be blank")
        }
        if (screen.schemaVersion > SduiScreen.SCHEMA_VERSION) {
            issues += ValidationIssue(
                IssueSeverity.WARNING,
                "screen.schemaVersion",
                "Payload targets schema v${screen.schemaVersion}; this SDK understands " +
                    "v${SduiScreen.SCHEMA_VERSION}. Unknown fields will be ignored.",
            )
        }

        fun visitAction(action: ActionSpec, path: String) {
            if (action.type.isBlank()) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, "Action type must not be blank")
            } else if (knownActions.isNotEmpty() && action.type !in knownActions) {
                issues += ValidationIssue(
                    IssueSeverity.ERROR, path,
                    "Unknown action '${action.type}'. Register a handler or remove it.",
                )
            }
            if (action.type == ActionTypes.API_CALL) {
                val api = (action.params["api"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (api != null && api !in screen.apis && !api.startsWith("{{")) {
                    issues += ValidationIssue(
                        IssueSeverity.ERROR, "$path.params.api",
                        "References api '$api' which is not declared in screen.apis",
                    )
                }
            }
            if (action.type == ActionTypes.NAVIGATE) {
                val target = (action.params["screen"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val mode = (action.params["mode"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val needsTarget = mode == null || mode !in setOf("pop", "popToRoot")
                if (needsTarget && target.isNullOrBlank()) {
                    issues += ValidationIssue(
                        IssueSeverity.ERROR, "$path.params.screen",
                        "navigate requires a target screen for mode '${mode ?: "push"}'",
                    )
                }
            }
            action.then.forEachIndexed { i, next -> visitAction(next, "$path.then[$i]") }
            action.otherwise.forEachIndexed { i, next -> visitAction(next, "$path.else[$i]") }
            action.onError.forEachIndexed { i, next -> visitAction(next, "$path.onError[$i]") }
        }

        fun visitNode(node: SduiNode, path: String, depth: Int, parentType: String?) {
            nodeCount++
            if (depth > maxDepth) {
                issues += ValidationIssue(
                    IssueSeverity.ERROR, path,
                    "Tree exceeds the maximum depth of $maxDepth",
                )
                return
            }
            if (nodeCount > maxNodes) return

            if (node.type.isBlank()) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, "Component type must not be blank")
            } else if (knownComponents.isNotEmpty() && node.type.lowercase() !in knownComponents) {
                issues += ValidationIssue(
                    IssueSeverity.ERROR, path,
                    "Unknown component '${node.type}'. Register a renderer or remove it.",
                )
            }

            node.id?.let { id ->
                seenIds.put(id, path)?.let { previous ->
                    issues += ValidationIssue(
                        IssueSeverity.WARNING, path,
                        "Duplicate id '$id' (also at $previous); state bindings may collide",
                    )
                }
            }

            if (node.modifier.weight != null && parentType !in FLEX_CONTAINERS) {
                issues += ValidationIssue(
                    IssueSeverity.WARNING, "$path.modifier.weight",
                    "weight has no effect outside row/column (parent is ${parentType ?: "root"})",
                )
            }

            node.actions.forEach { (event, action) -> visitAction(action, "$path.actions.$event") }
            node.repeat?.let { repeat ->
                if (!repeat.items.contains("{{")) {
                    issues += ValidationIssue(
                        IssueSeverity.WARNING, "$path.repeat.items",
                        "'${repeat.items}' is not a binding expression; the list will never update",
                    )
                }
            }

            node.children.forEachIndexed { index, child ->
                visitNode(child, "$path.children[$index]", depth + 1, node.type.lowercase())
            }
        }

        visitNode(screen.root, "root", 0, null)
        if (nodeCount > maxNodes) {
            issues += ValidationIssue(
                IssueSeverity.ERROR, "root",
                "Tree has more than $maxNodes nodes ($nodeCount); paginate instead",
            )
        }

        screen.onLoad.forEachIndexed { i, a -> visitAction(a, "screen.onLoad[$i]") }
        screen.onAppear.forEachIndexed { i, a -> visitAction(a, "screen.onAppear[$i]") }
        screen.apis.forEach { (key, api) ->
            if (api.url.isBlank()) {
                issues += ValidationIssue(IssueSeverity.ERROR, "apis.$key.url", "API url must not be blank")
            }
            api.onSuccess.forEachIndexed { i, a -> visitAction(a, "apis.$key.onSuccess[$i]") }
            api.onError.forEachIndexed { i, a -> visitAction(a, "apis.$key.onError[$i]") }
        }

        return ValidationReport(issues)
    }

    private companion object {
        val FLEX_CONTAINERS = setOf("row", "column", "lazycolumn", "lazyrow", "box")
    }
}
