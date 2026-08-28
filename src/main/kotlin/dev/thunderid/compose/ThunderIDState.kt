// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.User
import dev.thunderid.android.UserProfile
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Reactive auth state for Compose. Held inside [rememberThunderIDState]. */
@Stable
class ThunderIDState(
    val client: ThunderIDClient,
    val i18n: ThunderIDI18n,
    private val scope: CoroutineScope,
) {
    var user by mutableStateOf<User?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var isInitialized by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    /** Mirrors [dev.thunderid.android.ThunderIDConfig.fetchUserProfile]. */
    var fetchUserProfileEnabled: Boolean = true
        internal set

    val isSignedIn: Boolean get() = user != null

    internal suspend fun initialize(config: ThunderIDConfig) {
        isLoading = true
        try {
            fetchUserProfileEnabled = config.fetchUserProfile
            client.initialize(config)
            val signedIn = runCatching { client.isSignedIn() }.getOrDefault(false)
            user = if (signedIn) runCatching { client.getUser() }.getOrNull() else null
            if (signedIn && fetchUserProfileEnabled) launchUserProfileSync()
            isInitialized = true
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    suspend fun refresh() {
        if (!isInitialized) return
        isLoading = true
        try {
            val signedIn = client.isSignedIn()
            user = if (signedIn) client.getUser() else null
            if (signedIn && fetchUserProfileEnabled) launchUserProfileSync()
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    /** Merges [profile]'s attributes into [user]'s claims and syncs the client's cache to match. */
    internal fun mergeUserProfile(profile: UserProfile) {
        val current = user ?: return
        val merged = current.copy(claims = current.claims + profile.attributes)
        user = merged
        client.setCachedUser(merged)
    }

    // Launched on scope rather than awaited inline, since initialize()/refresh() are called
    // from screens (e.g. SignIn) that unmount and cancel their own rememberCoroutineScope()
    // right as user becomes non-null, which would cancel this fetch before it completes.
    private fun launchUserProfileSync() {
        scope.launch {
            val profile = runCatching { client.getUserProfile() }.getOrNull() ?: return@launch
            mergeUserProfile(profile)
        }
    }

    fun setLocale(locale: String) {
        i18n.setLocale(locale)
    }
}
