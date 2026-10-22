// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.resolveManagedIdentityName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetUsers

/**
 * Presents a page of users. Holds no data of its own: pass the [users] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen user. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [UserCreate].
 * @param content Renders custom UI in place of the default list, from the users, the loading and error state,
 *   and the pagination and selection handlers.
 */
@Composable
fun BaseUserList(
    users: List<ManagedUser>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((ManagedUser) -> Unit)? = null,
    selectedUserId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<ManagedUser>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = users,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: users.size,
                    selectedId = selectedUserId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "user-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.users.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "user-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.users.list.columns.name"), 2f) { Text(resolveManagedIdentityName(it)) },
                    AdminColumn(i18n.resolve("admin.users.list.columns.type")) { Text(it.type) },
                    AdminColumn(i18n.resolve("admin.common.columns.organization_unit")) { Text(it.ouHandle ?: it.ouId) },
                ),
            rows = users,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.users.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedUserId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: users.size, it) },
        )
    }
}

/**
 * Lists the users the signed-in user may see, a page at a time, through the user management operations.
 *
 * @param content Renders custom UI in place of the default list. See [BaseUserList].
 */
@Composable
fun UserList(
    modifier: Modifier = Modifier,
    filter: String? = null,
    limit: Int = 20,
    onSelect: ((ManagedUser) -> Unit)? = null,
    selectedUserId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<ManagedUser>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(filter, limit) { mutableStateOf(0) }
    val users = rememberGetUsers(limit = limit, offset = offset, filter = filter)
    BaseUserList(
        users = users.data?.users.orEmpty(),
        modifier = modifier,
        totalResults = users.data?.totalResults,
        isLoading = users.isLoading,
        error = toManagementErrorMessage(i18n, users.error, "admin.users.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedUserId = selectedUserId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
