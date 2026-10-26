// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.data.*

/** Density only limits verified labels. Score is rendered separately and is never ellipsized away. */
internal fun captionTags(post:Post,settings:AppSettings):List<Tag> {
    val limit=if(!settings.wrapTags)2 else when(settings.columns){1->20;2->10;3->3;else->2}
    val allowed=post.tags.filter {
        (it.category==TagCategory.COPYRIGHT && settings.showCopyright)||
            (it.category==TagCategory.ARTIST && settings.showArtist) ||
            (it.category in setOf(TagCategory.OTHER,TagCategory.UNKNOWN) && settings.showOtherTags)
    }
    val required=listOfNotNull(allowed.firstOrNull{it.category==TagCategory.COPYRIGHT},allowed.firstOrNull{it.category==TagCategory.ARTIST})
    val selected=(required+allowed).distinctBy{it.name}.take(limit).toSet()
    return allowed.filter{it in selected}
}
