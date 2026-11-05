// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp
import androidx.compose.ui.test.*
/** The fullscreen toolbar is intentionally outside the advanced panel's scroll container. */
internal fun SemanticsNodeInteraction.scrollIntoViewIfNeeded():SemanticsNodeInteraction{
    try{assertIsDisplayed()}catch(_:AssertionError){performScrollTo()}
    return this
}
