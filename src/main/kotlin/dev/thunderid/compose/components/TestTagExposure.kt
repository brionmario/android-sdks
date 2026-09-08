// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Publish the `Modifier.testTag` values in this subtree as Android resource IDs.
 *
 * Compose keeps `testTag` inside its own semantics tree, where only the Compose test framework
 * can read it. Anything driving the app through the platform accessibility tree — UI Automator,
 * and therefore black-box runners such as Maestro or Appium — sees nothing unless an ancestor
 * opts the subtree in, which is what this does.
 *
 * It is applied at the root of the components that tag flow-driven fields and actions, so a
 * consuming app gets addressable elements without having to know about this opt-in. The tags are
 * derived from the server's flow definition and carry no user data.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.exposeTestTagsAsResourceIds(): Modifier = semantics { testTagsAsResourceId = true }
