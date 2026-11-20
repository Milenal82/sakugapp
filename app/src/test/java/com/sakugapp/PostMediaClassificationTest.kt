// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.PostParser
import org.junit.Assert.*
import org.junit.Test

class PostMediaClassificationTest {
    private val post=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()
    @Test fun supportedExtensionsAndUrlFallbackKeepVideoDetectionAccurate(){
        for(extension in listOf("mp4","WEBM","MkV"))assertTrue(post.copy(fileUrl="https://example.com/a",fileExt=extension).isVideo)
        assertTrue(post.copy(fileUrl="https://example.com/a.MP4?token=redacted",fileExt=null).isVideo)
        assertTrue(post.copy(fileUrl="content://media/a.webm",fileExt=null).isVideo)
        assertFalse(post.copy(fileUrl=null,fileExt="mp4").isVideo)
        assertFalse(post.copy(fileUrl="https://example.com/a.mp4",fileExt="jpg").isVideo)
        assertFalse(post.copy(fileUrl="broken url",fileExt=null).isVideo)
        assertFalse(post.copy(fileUrl="https://example.com/a.jpg?name=x.mp4",fileExt=null).isVideo)
    }
    @Test fun mediaChangesAndScoreCopiesDoNotReuseAStaleClassification(){
        val video=post.copy(fileUrl="https://example.com/a.mp4",fileExt=null)
        assertTrue(video.isVideo);assertTrue(video.copy(score=99).isVideo)
        assertFalse(video.copy(fileUrl="https://example.com/a.jpg").isVideo)
    }
}
