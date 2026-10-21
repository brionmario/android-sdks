// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.BasicAgent
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetAgents

/**
 * Presents a page of agents. Holds no data of its own: pass the [agents] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen agent. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [AgentCreate].
 */
@Composable
fun BaseAgentList(
    agents: List<BasicAgent>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((BasicAgent) -> Unit)? = null,
    selectedAgentId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    AdminSection(
        block = "agent-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.agents.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "agent-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.agents.list.columns.name")) { Text(it.name) },
                    AdminColumn(i18n.resolve("admin.agents.list.columns.type")) { Text(it.type) },
                    AdminColumn(i18n.resolve("admin.agents.list.columns.client_id")) { Text(it.clientId.orEmpty()) },
                    AdminColumn(i18n.resolve("admin.common.columns.organization_unit")) { Text(it.ouHandle ?: it.ouId) },
                ),
            rows = agents,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.agents.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedAgentId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: agents.size, it) },
        )
    }
}

/** Lists agents, a page at a time, through the agent management operations. */
@Composable
fun AgentList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    onSelect: ((BasicAgent) -> Unit)? = null,
    selectedAgentId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit) { mutableStateOf(0) }
    val agents = rememberGetAgents(limit = limit, offset = offset)
    BaseAgentList(
        agents = agents.data?.agents.orEmpty(),
        modifier = modifier,
        totalResults = agents.data?.totalResults,
        isLoading = agents.isLoading,
        error = toManagementErrorMessage(i18n, agents.error, "admin.agents.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedAgentId = selectedAgentId,
        title = title,
        description = description,
        actions = actions,
    )
}
