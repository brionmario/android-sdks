// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.thunderid.android.management.RoleAssigneeType
import dev.thunderid.android.management.RoleAssignment
import dev.thunderid.android.management.RoleAssignmentInput
import dev.thunderid.android.management.resolveManagedIdentityName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.RoleAssignmentsInput
import dev.thunderid.compose.management.rememberAddRoleAssignments
import dev.thunderid.compose.management.rememberGetAgents
import dev.thunderid.compose.management.rememberGetGroups
import dev.thunderid.compose.management.rememberGetRoleAssignments
import dev.thunderid.compose.management.rememberGetUsers
import dev.thunderid.compose.management.rememberRemoveRoleAssignments
import kotlinx.coroutines.launch

private val ROLE_ASSIGNEE_TYPE_ORDER =
    listOf(RoleAssigneeType.USER, RoleAssigneeType.GROUP, RoleAssigneeType.AGENT, RoleAssigneeType.APP)

/**
 * Presents the identities a role is assigned to. Holds no data of its own.
 *
 * Assigning is offered only when both [onAssign] and [candidates] are set, for the assignee types
 * [candidates] holds. Unassigning is offered only when [onUnassign] is set.
 *
 * @param error A translated message to show in place of the assignments.
 * @param content Renders custom UI in place of the default table and picker, from the assignments, the
 *   candidates, the loading and error state, and the assign and unassign actions.
 */
@Composable
fun BaseRoleAssignments(
    assignments: List<RoleAssignment>,
    modifier: Modifier = Modifier,
    candidates: Map<RoleAssigneeType, List<IdentityCandidate>>? = null,
    error: String? = null,
    isLoading: Boolean = false,
    isMutating: Boolean = false,
    onAssign: ((RoleAssignmentInput) -> Unit)? = null,
    onUnassign: ((RoleAssignment) -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (RoleAssignmentsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                RoleAssignmentsState(
                    assignments = assignments,
                    candidates = candidates.takeIf { onAssign != null },
                    isLoading = isLoading,
                    error = error,
                    isMutating = isMutating,
                    onAssign = onAssign.takeIf { candidates != null },
                    onUnassign = onUnassign,
                ),
            )
        }
        return
    }
    val columns =
        mutableListOf<AdminColumn<RoleAssignment>>(
            AdminColumn(i18n.resolve("admin.roles.assignments.columns.assignee"), 2f) { Text(it.display ?: it.id) },
            AdminColumn(i18n.resolve("admin.roles.assignments.columns.type")) {
                Text(i18n.resolve("admin.common.identity_types.${it.type.value}"))
            },
        )
    if (onUnassign != null) {
        columns +=
            AdminColumn(i18n.resolve("admin.common.columns.actions")) { assignment ->
                val label = i18n.translate("admin.roles.assignments.remove_label", "name" to (assignment.display ?: assignment.id))
                TextButton(
                    onClick = { onUnassign(assignment) },
                    enabled = !isMutating,
                    modifier =
                        Modifier
                            .semantics { contentDescription = label }
                            .testTag(adminTag("role-assignments", "remove", assignment.id)),
                ) { Text(i18n.resolve("admin.common.actions.remove"), color = MaterialTheme.colorScheme.error) }
            }
    }
    AdminSection(
        block = "role-assignments",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.roles.assignments.title"),
        description = description,
    ) {
        if (onAssign != null && candidates != null) {
            AdminIdentityPicker(
                block = "role-assignments",
                addLabel = i18n.resolve("admin.roles.assignments.add"),
                types =
                    ROLE_ASSIGNEE_TYPE_ORDER
                        .filter { it in candidates }
                        .map { i18n.resolve("admin.common.identity_types.${it.value}") to it },
                candidates = candidates,
                excludedIds = assignments.map { it.id },
                isSubmitting = isMutating,
                onAdd = { id, type -> onAssign(RoleAssignmentInput(id, type)) },
            )
        }
        AdminTable(
            block = "role-assignments",
            columns = columns,
            rows = assignments,
            rowId = { "${it.type.value}:${it.id}" },
            emptyText = i18n.resolve("admin.roles.assignments.empty"),
            isLoading = isLoading,
            error = error,
        )
    }
}

/**
 * Shows who a role is assigned to through the role management operations.
 *
 * @param editable Lets the role be assigned to users, groups and agents, and be unassigned. Off by default.
 * @param content Renders custom UI in place of the default table and picker. See [BaseRoleAssignments].
 */
@Composable
fun RoleAssignments(
    roleId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    title: String? = null,
    description: String? = null,
    content: (@Composable (RoleAssignmentsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val assignments = rememberGetRoleAssignments(roleId, limit = 100)
    val users = rememberGetUsers(limit = 100, enabled = editable)
    val groups = rememberGetGroups(limit = 100, enabled = editable)
    val agents = rememberGetAgents(limit = 100, enabled = editable)
    val assign = rememberAddRoleAssignments()
    val unassign = rememberRemoveRoleAssignments()
    BaseRoleAssignments(
        assignments = assignments.data?.assignments.orEmpty(),
        modifier = modifier,
        candidates =
            if (editable) {
                mapOf(
                    RoleAssigneeType.USER to
                        users.data
                            ?.users
                            .orEmpty()
                            .map { IdentityCandidate(it.id, resolveManagedIdentityName(it)) },
                    RoleAssigneeType.GROUP to
                        groups.data
                            ?.groups
                            .orEmpty()
                            .map { IdentityCandidate(it.id, it.name) },
                    RoleAssigneeType.AGENT to
                        agents.data
                            ?.agents
                            .orEmpty()
                            .map { IdentityCandidate(it.id, it.name) },
                )
            } else {
                null
            },
        error =
            toManagementErrorMessage(i18n, assignments.error, "admin.roles.assignments.error")
                ?: toManagementErrorMessage(i18n, assign.error ?: unassign.error, "admin.roles.assignments.update_error"),
        isLoading = assignments.isLoading,
        isMutating = assign.isLoading || unassign.isLoading,
        onAssign =
            if (editable) {
                { assignment -> scope.launch { assign.mutate(RoleAssignmentsInput(roleId, listOf(assignment))) } }
            } else {
                null
            },
        onUnassign =
            if (editable) {
                { assignment ->
                    scope.launch {
                        unassign.mutate(RoleAssignmentsInput(roleId, listOf(RoleAssignmentInput(assignment.id, assignment.type))))
                    }
                }
            } else {
                null
            },
        title = title,
        description = description,
        content = content,
    )
}
