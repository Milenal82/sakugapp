// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

/** Hand-written protocol and fault cases, NOT captured production responses. See docs/data-source.md. */
class DataTest {
    @Test fun missingAndNullScoresRemainUnknown() {
        val posts = PostParser.posts("""[{"id":1},{"id":2,"score":null},{"id":3,"score":0},{"id":4,"score":-2}]""")
        assertEquals(listOf(null, null, 0, -2), posts.map { it.score })
    }
    @Test fun unparseableScoreDoesNotBecomeZero() {
        assertNull(PostParser.posts("""[{"id":1,"score":"unavailable"}]""").single().score)
    }
    @Test fun classificationComesFromSummaryIncludingAliases() {
        val summary = PostParser.tagSummary("""{"version":1,"data":"1`test_artist`test_alias` 3`test_title` 0`test_general` 88`test_unknown` "}""")
        val post = PostParser.posts("""[{"id":1,"tags":"test_alias test_title test_general missing test_unknown"}]""", summary).single()
        assertEquals(listOf(TagCategory.ARTIST, TagCategory.COPYRIGHT, TagCategory.OTHER,
            TagCategory.UNKNOWN, TagCategory.UNKNOWN), post.tags.map { it.category })
    }
    @Test fun unclassifiedNamesAreNeverGuessed() {
        val tags = PostParser.posts("""[{"id":1,"tags":"artist_name anime_title"}]""").single().tags
        assertTrue(tags.all { it.category == TagCategory.UNKNOWN })
    }
    @Test fun tagsHandleWhitespaceAndDuplicates() {
        val tags = PostParser.posts("""[{"id":1,"tags":"  a  b\na "}]""").single().tags
        assertEquals(listOf("a", "b"), tags.map { it.name })
    }
    @Test fun unsafeMediaUrlsAreRejected() {
        for (url in listOf("http://example.org/a", "javascript:alert(1)", "https://user:pass@example.org/a", "file:///tmp/a"))
            assertNull(PostParser.safeUrl(url))
        assertEquals("https://example.org/a.mp4", PostParser.safeUrl("https://example.org/a.mp4"))
    }
    @Test fun invalidDimensionsAndAbsentFieldsRemainAbsent() {
        val post = PostParser.posts("""[{"id":1,"preview_width":0,"height":-2}]""").single()
        assertNull(post.previewWidth); assertNull(post.height); assertNull(post.aspectRatio)
        assertNull(post.fileUrl); assertNull(post.source)
    }
    @Test fun thumbnailRatioUsesReturnedDimensions() {
        val post = PostParser.posts("""[{"id":1,"preview_width":320,"preview_height":180}]""").single()
        assertEquals(16f / 9f, post.aspectRatio!!, 0.001f)
    }
    @Test fun videoExtensionUsesActualReturnedUrlWithoutInventingAlternatives() {
        val p = PostParser.posts("""[{"id":1,"file_url":"https://example.org/a.webm?x=1"}]""").single()
        assertTrue(p.isVideo)
        assertFalse(p.copy(fileUrl = null, fileExt = "mp4").isVideo)
    }
    @Test fun malformedAndErrorResponsesFailInsteadOfGeneratingPosts() {
        for (body in listOf("<html>denied</html>", "{\"success\":false}", "[{\"score\":2}]", "[{\"id\":-1}]", "[null]")) {
            assertThrows(DataFailure::class.java) { PostParser.posts(body) }
        }
    }
    @Test fun malformedSummaryIsNotTrusted() {
        for (body in listOf("{}", "[]", """{"data":"artist`name` "}""", """{"data":"1`name"}"""))
            assertThrows(DataFailure::class.java) { PostParser.tagSummary(body) }
    }
    @Test fun emptyPageIsAnActualEmptyResult() { assertTrue(PostParser.posts("[]").isEmpty()) }
    @Test fun overlappingPagesAreDeduplicatedByIdAndOrderRetained() {
        val a = PostParser.posts("""[{"id":3},{"id":2}]""")
        val b = PostParser.posts("""[{"id":2},{"id":1}]""")
        val first = FeedState().append(PostPage(a, 2), 2)
        val next = first.append(PostPage(b, 2), 2)
        assertEquals(listOf(3L, 2L, 1L), next.posts.map { it.id })
        assertEquals(3, next.nextPage); assertFalse(next.endReached)
        val end = next.append(PostPage(emptyList(), 0), 2)
        assertTrue(end.endReached); assertEquals(next.posts, end.posts)
    }
    @Test fun duplicateOnlyPageStillAdvancesToAvoidRequestingSamePage() {
        val posts = PostParser.posts("""[{"id":3}]""")
        val result = FeedState(posts = posts, nextPage = 2).append(PostPage(posts, 1), 1)
        assertEquals(3, result.nextPage); assertEquals(1, result.posts.size); assertFalse(result.endReached)
    }
    @Test fun accessRestrictionsHaveExplicitMessages() {
        assertTrue(httpError(403).contains("不会绕过")); assertTrue(httpError(429).contains("频率"))
        assertTrue(httpError(503).contains("暂不可用")); assertTrue(httpError(421).contains("频率"))
    }
}
