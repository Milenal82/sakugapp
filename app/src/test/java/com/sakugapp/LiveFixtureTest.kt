// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

/** Public responses captured and sanitized by scripts/capture-public-fixture.py; see provenance.json. */
class LiveFixtureTest {
    private fun resource(name: String) = javaClass.getResource("/live/$name")!!.readText()
    @Test fun capturedPostsParseWithoutInventingScoresOrDimensions() {
        val posts = PostParser.posts(resource("posts.json"))
        assertEquals(listOf(319947L, 319946L), posts.map { it.id })
        assertEquals(listOf(33, 0), posts.map { it.score })
        assertEquals(860, posts.first().width)
        assertEquals(480, posts.first().height)
        assertEquals(150f / 84f, posts.first().aspectRatio!!, 0.001f)
        assertTrue(posts.all { it.isVideo })
        assertEquals("https://www.sakugabooru.com/post/show/319947", posts.first().pageUrl)
    }
    @Test fun capturedSummaryClassifiesActualArtistAndCopyrightTags() {
        val posts = PostParser.posts(resource("posts.json"), PostParser.tagSummary(resource("tags.json")))
        val tags = posts.first().tags.associate { it.name to it.category }
        assertEquals(TagCategory.ARTIST, tags["mokmok"])
        assertEquals(TagCategory.COPYRIGHT, tags["jujutsu_kaisen_season_3"])
        assertEquals(TagCategory.OTHER, tags["animated"])
        assertEquals(TagCategory.ARTIST, posts.last().tags.first { it.name == "artist_unknown" }.category)
    }
}
