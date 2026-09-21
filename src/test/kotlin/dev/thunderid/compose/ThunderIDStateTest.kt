// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose

import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.User
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/** Covers [ThunderIDState.getUserSchema]'s caching: the schema shared by every component that
 * needs it (e.g. [dev.thunderid.compose.components.presentation.user.UserProfile] and
 * [dev.thunderid.compose.components.presentation.user.ChangeCredential]) should be fetched once,
 * not once per component. */
class ThunderIDStateTest {
    private fun newState(
        client: ThunderIDClient,
        scope: CoroutineScope,
    ) = ThunderIDState(client = client, i18n = ThunderIDI18n(), scope = scope)

    @Test
    fun `getUserSchema fetches once and caches the result`() =
        runTest {
            val client = mockk<ThunderIDClient>()
            val schema = mapOf("password" to AttributeSchema(credential = true))
            coEvery { client.getUserSchema() } returns schema
            val state = newState(client, this)

            val first = state.getUserSchema()
            val second = state.getUserSchema()

            assertSame(first, second)
            assertEquals(schema, first)
            coVerify(exactly = 1) { client.getUserSchema() }
        }

    @Test
    fun `getUserSchema issues only one network call for several concurrent callers`() =
        runTest {
            val client = mockk<ThunderIDClient>()
            val schema = mapOf("pin" to AttributeSchema(credential = true))
            coEvery { client.getUserSchema() } returns schema
            val state = newState(client, this)

            // Models UserProfile and two ChangeCredential instances all mounting on the same
            // screen and each asking for the schema around the same time.
            val callers = List(3) { async { state.getUserSchema() } }

            callers.forEach { assertEquals(schema, it.await()) }
            coVerify(exactly = 1) { client.getUserSchema() }
        }

    @Test
    fun `getUserSchema does not cache a failed fetch`() =
        runTest {
            val client = mockk<ThunderIDClient>()
            val schema = mapOf("password" to AttributeSchema(credential = true))
            coEvery { client.getUserSchema() } throws RuntimeException("network error") andThen schema
            val state = newState(client, this)

            try {
                state.getUserSchema()
                fail("expected the first call to throw")
            } catch (e: RuntimeException) {
                assertEquals("network error", e.message)
            }

            val result = state.getUserSchema()

            assertEquals(schema, result)
            coVerify(exactly = 2) { client.getUserSchema() }
        }

    @Test
    fun `refresh clears the cached schema so the next access refetches`() =
        runTest {
            val client = mockk<ThunderIDClient>()
            val schema = mapOf("password" to AttributeSchema(credential = true))
            coEvery { client.getUserSchema() } returns schema
            coEvery { client.isSignedIn() } returns true
            coEvery { client.getUser() } returns User(mapOf("sub" to "u1"))
            val state = newState(client, this)
            state.isInitialized = true
            state.fetchUserProfileEnabled = false
            state.getUserSchema()

            state.refresh()

            assertNull(state.userSchema)
            coVerify(exactly = 1) { client.getUserSchema() }
        }
}
