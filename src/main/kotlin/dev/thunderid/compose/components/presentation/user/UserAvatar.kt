// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.user

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the current user's avatar: their real profile picture if one is available, otherwise
 * a deterministic two-color gradient circle with their initials (spec §8.4 Presentation).
 *
 * Styled, context-aware variant — reads the current user from [LocalThunderID].
 */
@Composable
fun UserAvatar(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val state = LocalThunderID.current
    BaseUserAvatar(user = state.user, size = size, modifier = modifier)
}

/**
 * Unstyled base variant (spec §8.3) — takes the user explicitly, no [LocalThunderID] dependency.
 *
 * Ported bit-for-bit from the web SDK's `Avatar` primitive (`generateBackgroundColor` +
 * `getInitials` in `packages/react/src/components/primitives/Avatar/Avatar.tsx`), so the same
 * seed name always resolves to the same gradient/initials pair across platforms.
 */
@Composable
fun BaseUserAvatar(
    user: User?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val name = resolveAvatarName(user)
    val pictureUrl = resolvePictureUrl(user)

    if (pictureUrl == null) {
        InitialsAvatar(name = name, size = size, modifier = modifier)
        return
    }

    // The gradient stands in while the picture loads, and stays if it cannot be loaded at all.
    SubcomposeAsyncImage(
        model = pictureUrl,
        imageLoader = avatarImageLoader(LocalContext.current),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(size)
                .clip(CircleShape),
        loading = { InitialsAvatar(name = name, size = size) },
        error = { InitialsAvatar(name = name, size = size) },
    )
}

/**
 * Image loader used for avatars, kept process wide so every avatar shares one memory and disk
 * cache. It adds SVG decoding, which the platform decoders do not cover, on top of Coil's
 * defaults for raster formats.
 */
private fun avatarImageLoader(context: Context): ImageLoader {
    avatarImageLoader?.let { return it }
    return synchronized(avatarImageLoaderLock) {
        avatarImageLoader ?: ImageLoader
            .Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .build()
            .also { avatarImageLoader = it }
    }
}

private val avatarImageLoaderLock = Any()

@Volatile
private var avatarImageLoader: ImageLoader? = null

@Composable
private fun InitialsAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val brush = rememberAvatarBrush(name = name, size = size)

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(brush)
                .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = getInitials(name),
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun rememberAvatarBrush(
    name: String,
    size: Dp,
): Brush {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val seed = avatarSeed(name)

    val hue1 = (2 * seed) % 360
    val hue2 = (hue1 + 60 + (seed % 120)) % 360
    val saturation = 70 + (seed % 20)
    val lightness1 = 55 + (seed % 15)
    val lightness2 = 60 + ((2 * seed) % 15)
    val angleDegrees = 45 + (seed % 91)

    val color1 = hslToColor(hue1, saturation, lightness1)
    val color2 = hslToColor(hue2, saturation, lightness2)

    val radians = angleDegrees * Math.PI / 180.0
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val start = Offset((0.5f - dx / 2f) * sizePx, (0.5f - dy / 2f) * sizePx)
    val end = Offset((0.5f + dx / 2f) * sizePx, (0.5f + dy / 2f) * sizePx)

    return Brush.linearGradient(colors = listOf(color1, color2), start = start, end = end)
}

/** JS-32-bit-signed-integer-string-hash based seed, matching the web SDK exactly. */
private fun avatarSeed(name: String): Int {
    var hash = 0
    for (c in name) {
        hash = (hash shl 5) - hash + c.code
    }
    return if (hash == Int.MIN_VALUE) 0 else abs(hash)
}

private fun getInitials(name: String): String =
    name
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

private fun resolveAvatarName(user: User?): String {
    val given = (user?.get("given_name") as? String)?.takeIf { it.isNotBlank() }
    val family = (user?.get("family_name") as? String)?.takeIf { it.isNotBlank() }
    if (given != null && family != null) {
        return "$given $family"
    }
    return user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.username?.takeIf { it.isNotBlank() }
        ?: user?.email?.takeIf { it.isNotBlank() }
        ?: "Guest"
}

private val pictureClaimKeys = listOf("picture", "profileUrl", "profile", "URL", "avatarUrl", "avatar")

private fun resolvePictureUrl(user: User?): String? {
    if (user == null) return null
    for (key in pictureClaimKeys) {
        val value = (user[key] as? String)?.takeIf { it.isNotBlank() }
        if (value != null) return value
    }
    return null
}

/** Standard HSL→RGB conversion, since Compose's [Color] only takes RGB/ARGB. */
private fun hslToColor(
    h: Int,
    s: Int,
    l: Int,
): Color {
    val hue = ((h % 360) + 360) % 360 / 360f
    val saturation = s / 100f
    val lightness = l / 100f

    if (saturation == 0f) {
        return Color(lightness, lightness, lightness)
    }

    val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    val r = hueToRgb(p, q, hue + 1f / 3f)
    val g = hueToRgb(p, q, hue)
    val b = hueToRgb(p, q, hue - 1f / 3f)
    return Color(r, g, b)
}

private fun hueToRgb(
    p: Float,
    q: Float,
    tInput: Float,
): Float {
    var t = tInput
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}
