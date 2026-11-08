// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

/** Inter-card spacing only. Outer insets and caption padding are independent. */
object GridSpacingPolicy {
    fun spacing(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 10f) else 8f
}
