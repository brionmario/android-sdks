// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.user

import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.User
import dev.thunderid.android.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {
    // ── buildProfileFields ──────────────────────────────────────────────────

    @Test
    fun `buildProfileFields skips credential attributes`() {
        val schema = mapOf("password" to AttributeSchema(credential = true))
        val profile = UserProfile(id = "u1", attributes = mapOf("password" to "secret"))

        val fields = buildProfileFields(schema, profile)

        assertTrue(fields.isEmpty())
    }

    @Test
    fun `buildProfileFields shows a non-credential schema attribute even if it would be on the JS fallback skip-list`() {
        // The fallback skip-list (roles.default, picture, etc.) only applies to JS's no-schema
        // rendering path. This component always renders from a real schema, so nothing beyond
        // credential attributes gets filtered out — e.g. "picture" must still render.
        val schema = mapOf("picture" to AttributeSchema(type = "STRING"))
        val profile = UserProfile(id = "u1", attributes = mapOf("picture" to "https://example.com/a.png"))

        val fields = buildProfileFields(schema, profile)

        assertEquals(1, fields.size)
        assertEquals("picture", fields[0].name)
    }

    @Test
    fun `buildProfileFields marks a field readonly from schema readOnly flag`() {
        val schema = mapOf("createdAt" to AttributeSchema(readOnly = true))
        val profile = UserProfile(id = "u1", attributes = mapOf("createdAt" to "2026-01-01"))

        val fields = buildProfileFields(schema, profile)

        assertEquals(1, fields.size)
        assertTrue(fields[0].isReadonly)
    }

    @Test
    fun `buildProfileFields marks a field readonly from schema mutability`() {
        val schema = mapOf("createdAt" to AttributeSchema(mutability = "READ_ONLY"))
        val profile = UserProfile(id = "u1", attributes = mapOf("createdAt" to "2026-01-01"))

        val fields = buildProfileFields(schema, profile)

        assertTrue(fields[0].isReadonly)
    }

    @Test
    fun `buildProfileFields marks a field readonly from the fixed readonly-list regardless of schema`() {
        val schema = mapOf("sub" to AttributeSchema(mutability = "READ_WRITE"))
        val profile = UserProfile(id = "u1", attributes = mapOf("sub" to "u1"))

        val fields = buildProfileFields(schema, profile)

        assertTrue(fields[0].isReadonly)
    }

    @Test
    fun `buildProfileFields leaves an editable field writable`() {
        val schema = mapOf("nickname" to AttributeSchema(mutability = "READ_WRITE"))
        val profile = UserProfile(id = "u1", attributes = mapOf("nickname" to "Nik"))

        val fields = buildProfileFields(schema, profile)

        assertEquals(false, fields[0].isReadonly)
    }

    @Test
    fun `buildProfileFields detects a multi-valued field from the raw attribute value`() {
        val schema = mapOf("phoneNumbers" to AttributeSchema())
        val profile = UserProfile(id = "u1", attributes = mapOf("phoneNumbers" to listOf("111", "222")))

        val fields = buildProfileFields(schema, profile)

        assertTrue(fields[0].isMultiValued)
    }

    // ── buildProfileFieldsFromClaims ────────────────────────────────────────

    @Test
    fun `buildProfileFieldsFromClaims strips protocol claims via User profileClaims`() {
        val user = User(mapOf("sub" to "u1", "exp" to 123L, "iat" to 456L, "given_name" to "Ada"))

        val fields = buildProfileFieldsFromClaims(user)

        assertEquals(listOf("given_name"), fields.map { it.name })
    }

    @Test
    fun `buildProfileFieldsFromClaims shows picture as a row, matching PR 25's original rendering`() {
        val user = User(mapOf("picture" to "https://example.com/a.png", "email" to "ada@example.com"))

        val fields = buildProfileFieldsFromClaims(user)

        assertEquals(listOf("email", "picture"), fields.map { it.name })
    }

    @Test
    fun `buildProfileFieldsFromClaims drops blank string values`() {
        val user = User(mapOf("given_name" to "", "email" to "ada@example.com"))

        val fields = buildProfileFieldsFromClaims(user)

        assertEquals(listOf("email"), fields.map { it.name })
    }

    @Test
    fun `buildProfileFieldsFromClaims drops nested object claims like assurance`() {
        // The real "assurance" claim decodes to an org.json.JSONObject, which formatClaim also
        // has no branch for; a plain Map exercises the same "unhandled type -> null" path
        // without needing org.json's stub-only implementation in a plain JVM unit test.
        val user = User(mapOf("assurance" to mapOf("aal" to "AAL1"), "email" to "ada@example.com"))

        val fields = buildProfileFieldsFromClaims(user)

        assertEquals(listOf("email"), fields.map { it.name })
    }

    @Test
    fun `buildProfileFieldsFromClaims marks every field readonly`() {
        val user = User(mapOf("given_name" to "Ada"))

        val fields = buildProfileFieldsFromClaims(user)

        assertTrue(fields[0].isReadonly)
    }

    @Test
    fun `buildProfileFieldsFromClaims derives a humanized display name from the claim key`() {
        val user = User(mapOf("given_name" to "Ada"))

        val fields = buildProfileFieldsFromClaims(user)

        assertEquals("Given Name", fields[0].schema.displayName)
    }

    // ── formatClaim ─────────────────────────────────────────────────────────

    @Test
    fun `formatClaim renders a boolean as Yes or No`() {
        assertEquals("Yes", formatClaim(true))
        assertEquals("No", formatClaim(false))
    }

    @Test
    fun `formatClaim joins a list`() {
        // Claims decoded from /oauth2/userinfo (Gson) surface array values as List; only claims
        // decoded from a raw JWT surface org.json.JSONArray, which formatClaim also handles but
        // can't be unit tested directly (org.json is a stub-only class outside a real Android runtime).
        assertEquals("a, b", formatClaim(listOf("a", "b")))
    }

    @Test
    fun `formatClaim drops a nested map`() {
        assertNull(formatClaim(mapOf("aal" to "AAL1")))
    }

    // ── claimsDisplayName ───────────────────────────────────────────────────

    @Test
    fun `claimsDisplayName joins given_name and family_name`() {
        val user = User(mapOf("given_name" to "Ada", "family_name" to "Lovelace"))

        assertEquals("Ada Lovelace", claimsDisplayName(user))
    }

    @Test
    fun `claimsDisplayName falls back to email local part when no name claims exist`() {
        val user = User(mapOf("email" to "ada@example.com"))

        assertEquals("ada", claimsDisplayName(user))
    }

    @Test
    fun `claimsDisplayName falls back to Guest for a null user`() {
        assertEquals("Guest", claimsDisplayName(null))
    }

    // ── validateField ────────────────────────────────────────────────────────

    @Test
    fun `validateField reports required error for a blank required field`() {
        val error = validateField(AttributeSchema(required = true), "  ")

        assertEquals("userProfile.validation.required", error)
    }

    @Test
    fun `validateField reports pattern error when regex does not match`() {
        val error = validateField(AttributeSchema(regex = "^[0-9]+$"), "abc")

        assertEquals("userProfile.validation.pattern", error)
    }

    @Test
    fun `validateField accepts a value matching the regex`() {
        val error = validateField(AttributeSchema(regex = "^[0-9]+$"), "12345")

        assertNull(error)
    }

    @Test
    fun `validateField ignores an invalid regex instead of blocking the save`() {
        val error = validateField(AttributeSchema(regex = "([unclosed"), "anything")

        assertNull(error)
    }

    @Test
    fun `validateField allows a blank optional field`() {
        val error = validateField(AttributeSchema(required = false), "")

        assertNull(error)
    }

    // ── mapAttribute ────────────────────────────────────────────────────────

    @Test
    fun `mapAttribute resolves the first matching default fallback path`() {
        val profile = UserProfile(id = "u1", attributes = mapOf("email" to "a@example.com"))

        val value = mapAttribute("email", emptyMap(), profile)

        assertEquals("a@example.com", value)
    }

    @Test
    fun `mapAttribute prefers the first candidate path over later ones`() {
        val profile =
            UserProfile(
                id = "u1",
                attributes = mapOf("emails" to "primary@example.com", "email" to "fallback@example.com"),
            )

        val value = mapAttribute("email", emptyMap(), profile)

        assertEquals("primary@example.com", value)
    }

    @Test
    fun `mapAttribute resolves a nested dot-path`() {
        val profile =
            UserProfile(
                id = "u1",
                attributes = mapOf("name" to mapOf("givenName" to "Ada")),
            )

        val value = mapAttribute("firstName", emptyMap(), profile)

        assertEquals("Ada", value)
    }

    @Test
    fun `mapAttribute honors a caller-supplied mapping override`() {
        val profile = UserProfile(id = "u1", attributes = mapOf("customEmail" to "custom@example.com"))

        val value = mapAttribute("email", mapOf("email" to listOf("customEmail")), profile)

        assertEquals("custom@example.com", value)
    }

    @Test
    fun `mapAttribute returns null when no candidate path resolves`() {
        val profile = UserProfile(id = "u1", attributes = emptyMap())

        val value = mapAttribute("email", emptyMap(), profile)

        assertNull(value)
    }

    // ── computeDisplayName ──────────────────────────────────────────────────

    @Test
    fun `computeDisplayName joins mapped first and last name`() {
        val profile =
            UserProfile(
                id = "u1",
                attributes = mapOf("name" to mapOf("givenName" to "Ada", "familyName" to "Lovelace")),
            )

        assertEquals("Ada Lovelace", computeDisplayName(emptyMap(), profile))
    }

    @Test
    fun `computeDisplayName falls back to username when no name is mapped`() {
        val profile = UserProfile(id = "u1", attributes = mapOf("userName" to "ada"))

        assertEquals("ada", computeDisplayName(emptyMap(), profile))
    }

    @Test
    fun `computeDisplayName falls back to the profile id as a last resort`() {
        val profile = UserProfile(id = "u1", attributes = emptyMap())

        assertEquals("u1", computeDisplayName(emptyMap(), profile))
    }

    // ── stringifyFieldValue / buildUpdatePayload ───────────────────────────

    @Test
    fun `stringifyFieldValue joins list values with a comma`() {
        assertEquals("111, 222", stringifyFieldValue(listOf("111", "222")))
    }

    @Test
    fun `buildUpdatePayload splits a comma-separated string for a multi-valued field`() {
        val payload = buildUpdatePayload("phoneNumbers", "111, 222", isMultiValued = true)

        assertEquals(listOf("111", "222"), payload["phoneNumbers"])
    }

    @Test
    fun `buildUpdatePayload nests a dot-path field name`() {
        val payload = buildUpdatePayload("name.givenName", "Ada", isMultiValued = false)

        @Suppress("UNCHECKED_CAST")
        val name = payload["name"] as Map<String, Any>
        assertEquals("Ada", name["givenName"])
    }

    // ── deepMergeAttributes ─────────────────────────────────────────────────

    @Test
    fun `deepMergeAttributes carries required base attributes through a single-field edit`() {
        // The backend rejects a save missing any required attribute, even for a single-field
        // edit, so a save must always carry the rest of the profile's attributes along.
        val base = mapOf("username" to "ada", "email" to "ada@example.com", "given_name" to "Ada")
        val overrides = mapOf("given_name" to "Ada Marie")

        val merged = deepMergeAttributes(base, overrides)

        assertEquals("ada", merged["username"])
        assertEquals("ada@example.com", merged["email"])
        assertEquals("Ada Marie", merged["given_name"])
    }

    @Test
    fun `deepMergeAttributes recursively merges a nested map without dropping sibling keys`() {
        val base = mapOf("name" to mapOf("givenName" to "Ada", "familyName" to "Lovelace"))
        val overrides = mapOf("name" to mapOf("givenName" to "Ada Marie"))

        val merged = deepMergeAttributes(base, overrides)

        @Suppress("UNCHECKED_CAST")
        val name = merged["name"] as Map<String, Any>
        assertEquals("Ada Marie", name["givenName"])
        assertEquals("Lovelace", name["familyName"])
    }

    @Test
    fun `deepMergeAttributes overwrites a non-map value with the override`() {
        val merged = deepMergeAttributes(mapOf("picture" to "old-url"), mapOf("picture" to "new-url"))

        assertEquals("new-url", merged["picture"])
    }
}
