// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.R
import dev.thunderid.android.User
import dev.thunderid.android.UserProfile
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.launch

// Attribute names that are always readonly regardless of schema mutability (data contract).
private val readonlyFields = setOf("attributes", "id", "isReadOnly", "ouId", "username", "sub")

// Default logical-name -> attribute-path fallback mappings.
private val defaultAttributeMappings: Map<String, List<String>> =
    mapOf(
        "email" to listOf("emails", "email"),
        "firstName" to listOf("name.givenName", "given_name"),
        "lastName" to listOf("name.familyName", "family_name"),
        "picture" to listOf("profile", "profileUrl", "picture", "URL"),
        "username" to listOf("userName", "username", "user_name"),
    )

/** A schema-described profile field merged with its current value, ready to render. */
data class ProfileField(
    val name: String,
    val schema: AttributeSchema,
    val rawValue: Any?,
    val isReadonly: Boolean,
    val isMultiValued: Boolean,
)

/**
 * Builds the ordered, filtered list of fields to render from the schema and current profile
 * attributes. Every non-credential schema attribute is shown by default.
 */
internal fun buildProfileFields(
    schema: Map<String, AttributeSchema>,
    profile: UserProfile,
): List<ProfileField> =
    schema.entries
        .filter { (_, attr) -> attr.credential != true }
        .sortedBy { it.key }
        .map { (name, attr) ->
            val rawValue = profile.attributes[name]
            ProfileField(
                name = name,
                schema = attr,
                rawValue = rawValue,
                isReadonly = attr.readOnly == true || attr.mutability == "READ_ONLY" || name in readonlyFields,
                isMultiValued = rawValue is List<*>,
            )
        }

/** Builds a read-only field list directly from JWT/userinfo claims (no schema to save against). */
internal fun buildProfileFieldsFromClaims(user: User?): List<ProfileField> =
    (user?.profileClaims ?: emptyMap())
        .mapNotNull { (key, value) -> formatClaim(value)?.let { key to it } }
        .sortedBy { (key, _) -> claimLabel(key).lowercase() }
        .map { (key, formatted) ->
            ProfileField(
                name = key,
                schema = AttributeSchema(displayName = claimLabel(key), readOnly = true, type = "STRING"),
                rawValue = formatted,
                isReadonly = true,
                isMultiValued = false,
            )
        }

internal fun formatClaim(value: Any?): String? =
    when (value) {
        is String -> {
            value.takeIf { it.isNotEmpty() }
        }

        is Boolean -> {
            if (value) "Yes" else "No"
        }

        is Number -> {
            value.toString()
        }

        is org.json.JSONArray -> {
            (0 until value.length())
                .mapNotNull { idx -> formatClaim(value.opt(idx)) }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        }

        is List<*> -> {
            value
                .mapNotNull { formatClaim(it) }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        }

        else -> {
            null
        }
    }

/** Humanizes a claim key for display: `given_name` -> "Given Name". */
internal fun claimLabel(key: String): String =
    key
        .replace("_", " ")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

internal fun claimsDisplayName(user: User?): String {
    if (user == null) return "Guest"
    val given = user["given_name"] as? String ?: ""
    val family = user["family_name"] as? String ?: ""
    val full = listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
    return full.ifEmpty {
        user.displayName?.takeIf { it.isNotEmpty() } ?: user.username ?: user.email?.substringBefore("@") ?: "Guest"
    }
}

/** Validates an edited field value against its schema: required first, then regex. */
internal fun validateField(
    schema: AttributeSchema,
    value: String,
): String? {
    val trimmed = value.trim()
    if (schema.required == true && trimmed.isEmpty()) {
        return "userProfile.validation.required"
    }
    val pattern = schema.regex
    if (!pattern.isNullOrEmpty() && trimmed.isNotEmpty()) {
        val matches = runCatching { Regex(pattern).containsMatchIn(trimmed) }.getOrNull()
        if (matches == false) {
            return "userProfile.validation.pattern"
        }
    }
    return null
}

/**
 * Resolves a logical attribute name (firstName, email, picture...) to a value on [profile] by
 * trying each candidate path in [mappings] in order, falling back to the built-in defaults.
 */
internal fun mapAttribute(
    key: String,
    mappings: Map<String, List<String>>,
    profile: UserProfile,
): String? {
    val candidates = mappings[key] ?: defaultAttributeMappings[key]
    if (candidates == null) {
        return profile.attributes[key]?.toString()
    }
    candidates.forEach { path ->
        resolveAttributePath(profile.attributes, path)?.let { return it.toString() }
    }
    return null
}

/** Combines mapped firstName/lastName into a display name, falling back to username then id. */
internal fun computeDisplayName(
    mappings: Map<String, List<String>>,
    profile: UserProfile,
): String {
    val firstName = mapAttribute("firstName", mappings, profile)
    val lastName = mapAttribute("lastName", mappings, profile)
    val fullName = listOfNotNull(firstName, lastName).joinToString(" ").trim()
    if (fullName.isNotEmpty()) return fullName
    return mapAttribute("username", mappings, profile) ?: profile.id
}

private fun resolveAttributePath(
    attributes: Map<String, Any>,
    path: String,
): Any? {
    var current: Any? = attributes
    path.split(".").forEach { segment ->
        current =
            when (val step = current) {
                is Map<*, *> -> step[segment]
                else -> return null
            }
    }
    return current
}

/** Renders a raw field value for display/editing: joins list values, blanks out complex ones. */
fun stringifyFieldValue(rawValue: Any?): String =
    when (rawValue) {
        null -> ""
        is List<*> -> rawValue.joinToString(", ") { it.toString() }
        is Map<*, *> -> ""
        else -> rawValue.toString()
    }

/** Builds the nested `{"attributes": {...}}` payload segment for a single dot-path field save. */
internal fun buildUpdatePayload(
    name: String,
    value: String,
    isMultiValued: Boolean,
): Map<String, Any> {
    val resolvedValue: Any =
        if (isMultiValued) {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            value
        }
    return buildNestedMap(name.split("."), resolvedValue)
}

private fun buildNestedMap(
    segments: List<String>,
    value: Any,
): Map<String, Any> =
    if (segments.size == 1) {
        mapOf(segments[0] to value)
    } else {
        mapOf(segments[0] to buildNestedMap(segments.drop(1), value))
    }

// Recursively merges overrides onto base. The backend requires every required attribute present
// in any save, so a single-field edit still needs the rest of the profile's attributes carried along.
internal fun deepMergeAttributes(
    base: Map<String, Any>,
    overrides: Map<String, Any>,
): Map<String, Any> {
    val result = base.toMutableMap()
    overrides.forEach { (key, value) ->
        val baseValue = result[key]
        @Suppress("UNCHECKED_CAST")
        result[key] =
            if (baseValue is Map<*, *> && value is Map<*, *>) {
                deepMergeAttributes(baseValue as Map<String, Any>, value as Map<String, Any>)
            } else {
                value
            }
    }
    return result
}

/** State passed to the [BaseUserProfile] builder slot. */
@Stable
class UserProfileState {
    var profile by mutableStateOf<UserProfile?>(null)
        internal set
    var fields by mutableStateOf<List<ProfileField>>(emptyList())
        internal set
    var displayName by mutableStateOf("")
        internal set
    var email by mutableStateOf<String?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    internal val editedValues = mutableStateMapOf<String, String>()
    internal val editingFields = mutableStateMapOf<String, Boolean>()
    internal val fieldErrors = mutableStateMapOf<String, String>()

    internal var onEdit: (String) -> Unit = {}
    internal var onCancel: (String) -> Unit = {}
    internal var onFieldChange: (String, String) -> Unit = { _, _ -> }
    internal var onSave: (String) -> Unit = {}

    fun isEditing(name: String): Boolean = editingFields[name] == true

    fun fieldValue(field: ProfileField): String = editedValues[field.name] ?: stringifyFieldValue(field.rawValue)

    fun fieldError(name: String): String? = fieldErrors[name]

    fun edit(name: String) = onEdit(name)

    fun cancel(name: String) = onCancel(name)

    fun setFieldValue(
        name: String,
        value: String,
    ) = onFieldChange(name, value)

    fun save(name: String) = onSave(name)
}

/** Editable, schema-driven user profile form (spec §8.4 Presentation). */
@Composable
fun UserProfile(
    modifier: Modifier = Modifier,
    attributeMapping: Map<String, List<String>> = emptyMap(),
    onSaved: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    BaseUserProfile(
        modifier = modifier,
        attributeMapping = attributeMapping,
        onSaved = onSaved,
        onError = onError,
    ) { state ->
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(i18n.resolve("userProfile.title"), style = MaterialTheme.typography.titleLarge)
            when {
                state.isLoading && state.profile == null -> {
                    Text(i18n.resolve("userProfile.loading"))
                }

                state.error != null -> {
                    Text(state.error ?: i18n.resolve("userProfile.error.load"))
                }

                else -> {
                    if (state.displayName.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UserAvatar(size = 64.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                            state.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                        HorizontalDivider()
                    }
                    state.fields.forEach { field ->
                        ProfileFieldRow(field = field, state = state, i18n = i18n)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFieldRow(
    field: ProfileField,
    state: UserProfileState,
    i18n: ThunderIDI18n,
) {
    val label = field.schema.displayName ?: field.schema.description ?: field.name
    val isComplex = field.schema.type == "COMPLEX" && field.rawValue is Map<*, *>
    val isEditing = state.isEditing(field.name)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    field.schema.type == "COMPLEX" && field.rawValue is Map<*, *> -> {
                        ComplexValueView(value = field.rawValue)
                    }

                    isEditing && !field.isReadonly -> {
                        ProfileFieldEditor(field = field, state = state)
                    }

                    else -> {
                        Text(stringifyFieldValue(field.rawValue).ifEmpty { "-" })
                    }
                }
            }
            when {
                isComplex -> {
                    Unit
                }

                isEditing && !field.isReadonly -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { state.save(field.name) }) { Text(i18n.resolve("userProfile.save")) }
                        TextButton(onClick = { state.cancel(field.name) }) { Text(i18n.resolve("userProfile.cancel")) }
                    }
                }

                !field.isReadonly -> {
                    IconButton(onClick = { state.edit(field.name) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = i18n.resolve("userProfile.edit"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        state.fieldError(field.name)?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileFieldEditor(
    field: ProfileField,
    state: UserProfileState,
) {
    val value = state.fieldValue(field)
    if (field.schema.type == "BOOLEAN") {
        Checkbox(
            checked = value == "true",
            onCheckedChange = { state.setFieldValue(field.name, it.toString()) },
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = { state.setFieldValue(field.name, it) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("thunderid-field-${field.name}")
                    .semantics { contentDescription = field.name },
            singleLine = true,
        )
    }
}

@Composable
private fun ComplexValueView(value: Map<*, *>) {
    Column {
        value.entries.forEach { (key, entryValue) ->
            Row {
                Text("$key: ", style = MaterialTheme.typography.labelSmall)
                Text(entryValue.toString())
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserProfile(
    modifier: Modifier = Modifier,
    attributeMapping: Map<String, List<String>> = emptyMap(),
    onSaved: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    content: @Composable (UserProfileState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    val scope = rememberCoroutineScope()
    val state = remember { UserProfileState() }
    var schema by remember { mutableStateOf<Map<String, AttributeSchema>>(emptyMap()) }

    fun applyProfile(
        loadedSchema: Map<String, AttributeSchema>,
        loadedProfile: UserProfile,
    ) {
        state.profile = loadedProfile
        state.fields = buildProfileFields(loadedSchema, loadedProfile)
        state.displayName = computeDisplayName(attributeMapping, loadedProfile)
        state.email = mapAttribute("email", attributeMapping, loadedProfile)

        // Reflect an edit (e.g. picture) immediately, without waiting for the next refresh.
        thunderState.mergeUserProfile(loadedProfile)
    }

    fun editField(name: String) {
        val field = state.fields.firstOrNull { it.name == name } ?: return
        state.editedValues[name] = stringifyFieldValue(field.rawValue)
        state.editingFields[name] = true
        state.fieldErrors.remove(name)
    }

    fun cancelField(name: String) {
        state.editingFields[name] = false
        state.editedValues.remove(name)
        state.fieldErrors.remove(name)
    }

    fun changeField(
        name: String,
        value: String,
    ) {
        state.editedValues[name] = value
    }

    fun saveField(name: String) {
        val field = state.fields.firstOrNull { it.name == name } ?: return
        val value = state.editedValues[name] ?: stringifyFieldValue(field.rawValue)
        val validationKey = validateField(field.schema, value)
        if (validationKey != null) {
            state.fieldErrors[name] = i18n.resolve(validationKey)
            return
        }
        state.fieldErrors.remove(name)
        scope.launch {
            state.isLoading = true
            try {
                val fieldPayload = buildUpdatePayload(name, value, field.isMultiValued)
                val currentAttributes = state.profile?.attributes ?: emptyMap()
                val payload = deepMergeAttributes(currentAttributes, fieldPayload)
                val updated = thunderState.client.updateUserProfile(payload)
                applyProfile(schema, updated)
                state.editingFields[name] = false
                state.editedValues.remove(name)
                onSaved?.invoke()
            } catch (e: Exception) {
                state.fieldErrors[name] = e.message ?: i18n.resolve("userProfile.error.save")
                onError?.invoke()
            } finally {
                state.isLoading = false
            }
        }
    }

    state.onEdit = ::editField
    state.onCancel = ::cancelField
    state.onFieldChange = ::changeField
    state.onSave = ::saveField

    // No /users/me - render from thunderState.user's claims. Keyed on thunderState.user
    // since it can still be loading when this composable first mounts.
    LaunchedEffect(thunderState.user, thunderState.fetchUserProfileEnabled) {
        if (thunderState.fetchUserProfileEnabled) return@LaunchedEffect
        val user = thunderState.user
        state.fields = buildProfileFieldsFromClaims(user)
        state.displayName = claimsDisplayName(user)
        state.email = user?.email
        state.error = null
        state.isLoading = false
    }

    LaunchedEffect(Unit) {
        if (!thunderState.fetchUserProfileEnabled) return@LaunchedEffect
        state.isLoading = true
        state.error = null
        try {
            val loadedSchema = thunderState.client.getUserSchema()
            val loadedProfile = thunderState.client.getUserProfile()
            schema = loadedSchema
            applyProfile(loadedSchema, loadedProfile)
        } catch (e: Exception) {
            state.error = e.message
        } finally {
            state.isLoading = false
        }
    }

    Box(modifier = modifier) { content(state) }
}
