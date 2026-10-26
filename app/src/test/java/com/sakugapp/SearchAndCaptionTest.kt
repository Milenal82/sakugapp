// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.graphics.Color
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SearchAndCaptionTest {
    @Test fun publicSummaryWithUnicodeSeparatorKeepsOtherRecordsAndDoesNotInventSplitNames() {
        val body=javaClass.getResource("/live/tag-summary-malformed-record.json")!!.readText()
        val types=PostParser.tagSummary(body)
        assertEquals(TagCategory.COPYRIGHT,types["bakemonogatari"])
        assertEquals(TagCategory.ARTIST,types["you_yoshinari"])
        assertEquals(TagCategory.ARTIST,types["mokmok"])
        for(name in listOf("emil_lundmark","laura_begliomini","emil_lundmark\u2028laura_begliomini"))assertFalse(types.containsKey(name))
    }
    @Test fun invalidSummaryRecordCannotEraseValidNeighboringTypes() {
        val types=PostParser.tagSummary("""{"data":"1`before` 1`broken 3`after` "}""")
        assertEquals(mapOf("before" to TagCategory.ARTIST,"after" to TagCategory.COPYRIGHT),types)
    }
    @Test fun successfulLaterClassificationRepairsEarlierCardsWithoutLosingPagination() {
        val original=PostParser.posts("""[{"id":10,"tags":"real_name","score":-4}]""")
        val state=FeedState(posts=original,nextPage=3).append(PostPage(emptyList(),0,classifications=mapOf("real_name" to TagCategory.ARTIST)),20)
        assertEquals(4,state.nextPage);assertEquals(-4,state.posts.single().score)
        assertEquals(TagCategory.ARTIST,state.posts.single().tags.single().category)
    }
    @Test fun captionIncludesOnlyVerifiedCategoriesAndBoundsDenseGrids() {
        val types=PostParser.tagSummary(javaClass.getResource("/live/tags.json")!!.readText())
        val post=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText(),types).first()
        assertEquals(listOf("jujutsu_kaisen_season_3","jujutsu_kaisen_series","mokmok"),captionTags(post,AppSettings()).map{it.name})
        assertEquals(2,captionTags(post,AppSettings(columns=4)).size)
        assertTrue(captionTags(post,AppSettings(showArtist=false)).none{it.category==TagCategory.ARTIST})
        assertTrue(captionTags(post.copy(tags=listOf(Tag("unknown",TagCategory.UNKNOWN))),AppSettings()).isEmpty())
        assertEquals(33,post.score)
    }
    @Test fun openingFiltersKeepsAllExistingOperatorsRangesAndNegativeRating() {
        val query="~tag1 ~tag2 -tag3 tag4* -rating:questionable id:>=100 source:http://site.com order:mpixels"
        assertEquals(query,SearchSyntax.filters(query))
        assertEquals("~tag1 ~tag2 -tag3 tag4* -rating:questionable id:>=100 source:http://site.com order:portrait",SearchSyntax.filters(query,"portrait"))
        assertEquals("~tag1 ~tag2 -tag3 tag4* id:>=100 source:http://site.com order:mpixels rating:safe",SearchSyntax.filters(query,rating="safe"))
    }
    @Test fun builderAndSuggestionsPreserveOrAndExclusionPrefixes() {
        assertEquals("~a ~b",SearchSyntax.add("~a","b","or"))
        assertEquals("a b",SearchSyntax.add("a","b","and"))
        assertEquals("a -b",SearchSyntax.add("a","b","exclude"))
        assertEquals("base ~mokmok",SearchSyntax.suggestion("base ~mok","mokmok"))
        assertEquals("base -mokmok",SearchSyntax.suggestion("base -mok","mokmok"))
        assertEquals("mok",SearchSyntax.suggestionPrefix("~mok"));assertNull(SearchSyntax.suggestionPrefix("source:http://site.com"))
    }
    @Test fun constraintsAreWarningsAndNeverRewriteTheQuery() {
        for(query in listOf("gun* dress","~gun dress","rating:safe rating:questionable","order:vote")) {
            assertFalse(SearchSyntax.warnings(query).isEmpty());assertEquals(query,SearchSyntax.filters(query))
        }
        for(query in listOf("~gun ~dress","rating:questionable score:100.. id:..1000","vote:3:bob order:vote"))assertTrue(SearchSyntax.warnings(query).isEmpty())
    }
    @Test fun allReferenceExpressionsReachTheApiWithLiteralOperatorsNotUrlInjection()=runBlocking {
        MockWebServer().use { server ->
            server.start();val repo=SakugaRepository(server.url("/"))
            for(example in SearchGuide.examples) {
                server.enqueue(MockResponse().setBody("[]"));repo.page(example.query,1)
                assertEquals(example.query,server.takeRequest().requestUrl!!.queryParameter("tags"))
            }
            val query="source:https://site.com/?q=a&next=b id:>=100 ~tag1 ~tag2"
            server.enqueue(MockResponse().setBody("[]"));repo.page(query,2)
            val request=server.takeRequest().requestUrl!!
            assertEquals(query,request.queryParameter("tags"));assertNull(request.queryParameter("next"));assertEquals("2",request.queryParameter("page"))
        }
    }
    @Test fun explicitOrderingReplacesOnlyTheSelectedField() {
        for(order in listOf("id","id_desc","score","score_asc","mpixels","mpixels_asc","landscape","portrait","vote")) {
            assertEquals("vote:3:bob id:100..200 order:$order",SearchSyntax.filters("vote:3:bob id:100..200 order:score",order))
        }
        assertEquals("-tag id:>=100",SearchSyntax.filters("-tag -rating:questionable id:>=100 order:score","date","all"))
    }
}
