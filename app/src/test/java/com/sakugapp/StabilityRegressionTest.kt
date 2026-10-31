// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

class StabilityRegressionTest {
    @Test fun aLongBrowseKeepsABoundedUniqueWindowWithoutResettingPagination(){
        val sample=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()
        var state=FeedState(query="animated order:score")
        repeat(30){page->
            // Synthetic IDs exercise retention only; no generated posts enter application data.
            val posts=(1..60).map{sample.copy(id=page*60L+it)}
            state=state.append(PostPage(posts,60),60)
            assertTrue(state.posts.size<=FeedState.MAX_RETAINED_POSTS)
            assertEquals(state.posts.size,state.posts.distinctBy{it.id}.size)
        }
        assertEquals(600,state.posts.size);assertEquals(1200,state.discardedPosts)
        assertEquals(31,state.nextPage);assertEquals(1201L,state.posts.first().id)
        assertEquals("animated order:score",state.query)
        val duplicate=state.append(PostPage(state.posts.takeLast(60),60),60)
        assertEquals(state.posts,duplicate.posts);assertTrue(duplicate.autoLoadPaused)
        assertEquals(32,duplicate.nextPage)
    }
}
