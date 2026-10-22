// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp
import com.sakugapp.data.*
import org.junit.Test
import org.junit.Assert.*

class PreferencesAndFramesTest {
    @Test fun historyIsRecentUniqueBoundedAndSkipsEmpty(){
        assertEquals(listOf("new","old"),PreferenceRules.history(listOf("old","new")," new "))
        assertEquals(listOf("old"),PreferenceRules.history(listOf("old")," "))
        assertEquals(30,PreferenceRules.history((1..30).map{"tag$it"},"new").size)
    }
    @Test fun traditionalChineseIsNativeWhileWebsiteLanguagesRemainOfficial(){
        assertEquals(setOf("en","ja","zh-TW"),PreferenceRules.languages)
        assertEquals("en",PreferenceRules.siteLanguage("zh-TW"));assertEquals("ja",PreferenceRules.siteLanguage("ja"))
        assertEquals("zh-TW,zh;q=0.9,en;q=0.7",PreferenceRules.acceptLanguage("zh-TW"))
    }
    @Test fun advancedFiltersPreserveTagsAndExclusions(){assertEquals("artist -genga order:score rating:safe",PreferenceRules.advanced("artist -genga order:random rating:explicit","score","safe"))}
    @Test fun unsupportedFiltersAreRejected(){assertThrows(IllegalArgumentException::class.java){PreferenceRules.advanced("","invented","safe")}}
    @Test fun exactIndexMovesByFramesAndClampsWithoutFps(){
        val index=FrameIndex(12,7);assertEquals(8,index.move(1).index);assertEquals(7,index.move(1).move(-1).index)
        assertEquals(0,index.move(-100).index);assertEquals(11,index.move(100).index)
    }
    @Test fun noFrameIsInventedForUnknownOrEmptyFrameCount(){assertThrows(IllegalArgumentException::class.java){FrameIndex(0)};assertThrows(IllegalArgumentException::class.java){FrameIndex(4,4)}}
    @Test fun browserRejectsUntrustedOriginsAndNonHttps(){
        for(url in listOf("https://www.sakugabooru.com.evil.org/user/home","https://www.sakugabooru.com@evil.org/","http://www.sakugabooru.com/","javascript:alert(1)","file:///tmp/a","https://www.sakugabooru.com:444/user/home"))assertFalse(UrlPolicy.trusted(url))
        assertTrue(UrlPolicy.trusted("https://blog.sakugabooru.com/"))
    }
    @Test fun postsAndPostLinksReturnToNativeScreens(){
        assertTrue(UrlPolicy.isPosts("https://www.sakugabooru.com/post?tags=animated"))
        assertEquals("a&b",UrlPolicy.tags("https://www.sakugabooru.com/post?tags=a%26b"))
        assertEquals(319947L,UrlPolicy.postId("https://www.sakugabooru.com/post/show/319947/title"))
        assertNull(UrlPolicy.postId("https://evil.org/post/show/319947"))
    }
    @Test fun languageIsAppliedToRealSiteQueryNotAnAnchor(){
        assertEquals("https://www.sakugabooru.com/forum?locale=ja",UrlPolicy.localized("https://www.sakugabooru.com/forum","ja"))
        assertEquals("https://www.sakugabooru.com/forum?locale=en",UrlPolicy.localized("https://www.sakugabooru.com/forum","zh-TW"))
        assertThrows(IllegalArgumentException::class.java){UrlPolicy.localized("https://www.sakugabooru.com/forum","zh")}
    }
}
