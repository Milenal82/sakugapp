// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import com.sakugapp.data.*

/** Only verified copyright/artist names and the server score belong below the thumbnail. */
internal fun postCaption(post: Post, settings: AppSettings): AnnotatedString = buildAnnotatedString {
    for (tag in post.tags.filter {
        (it.category == TagCategory.COPYRIGHT && settings.showCopyright) ||
            (it.category == TagCategory.ARTIST && settings.showArtist)
    }) {
        if (length > 0) append(" ")
        pushStringAnnotation("tag", tag.name)
        withStyle(SpanStyle(color=if(tag.category == TagCategory.COPYRIGHT) Color(0xFFFFD54F) else Color(0xFFCE93D8))) { append(tag.name) }
        pop()
    }
    if(settings.showScore) { if(length > 0) append(" ");append("Score ${post.score?.toString() ?: "?"}") }
}
