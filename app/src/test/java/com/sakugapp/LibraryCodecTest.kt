// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.google.gson.JsonParser
import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

class LibraryCodecTest {
    private fun post()=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()
    private fun backup()=LibraryBackup(listOf(FavoriteFolder(1,"",0),FavoriteFolder(8,"动画研究",1)),listOf(FavoritePost(post().id,LibraryCodec.encodePost(post()),100)),listOf(FavoriteLink(post().id,1,100),FavoriteLink(post().id,8,101)),listOf(LocalTagTranslation("original_tag","本地译名")))
    @Test fun capturedPostRoundTripsWithoutInventingMissingScoreOrDuration(){
        val p=post();assertEquals(p,LibraryCodec.decodePost(LibraryCodec.encodePost(p)))
        val unknown=p.copy(score=null,durationMs=null)
        assertNull(LibraryCodec.decodePost(LibraryCodec.encodePost(unknown)).score)
        assertNull(LibraryCodec.decodePost(LibraryCodec.encodePost(unknown)).durationMs)
        val imported=LibraryCodec.decode(LibraryCodec.encode(backup()))
        assertEquals(p,LibraryCodec.decodePost(imported.posts.single().snapshot));assertEquals(2,imported.links.size)
    }
    @Test fun repeatedIdsAreDeduplicatedButRelationshipsRemainManyToMany(){
        val original=backup();val imported=LibraryCodec.decode(LibraryCodec.encode(original.copy(posts=original.posts+original.posts,links=original.links+original.links)))
        assertEquals(1,imported.posts.size);assertEquals(2,imported.links.size)
    }
    @Test fun malformedOrForeignMediaBackupIsRejectedBeforeAnyDatabaseWork(){
        for(url in listOf("http://www.sakugabooru.com/a.mp4","https://www.sakugabooru.com.evil.invalid/a.mp4","https://127.0.0.1/a.mp4","file:///data/private.mp4","https://user:pass@www.sakugabooru.com/a.mp4")){
            assertThrows(LibraryFailure::class.java){LibraryCodec.decodePost(LibraryCodec.encodePost(post().copy(fileUrl=url)))}
        }
        val broken=backup().copy(links=listOf(FavoriteLink(999,1,10)))
        assertThrows(LibraryFailure::class.java){LibraryCodec.decode(LibraryCodec.encode(broken))}
        val version=JsonParser.parseString(LibraryCodec.encode(backup())).asJsonObject.apply{addProperty("version",999)}
        assertThrows(LibraryFailure::class.java){LibraryCodec.decode(version.toString())}
    }
    @Test fun badTypesOverflowAndControlCharactersCannotBeSmuggledIntoImportedMetadata(){
        val encoded=JsonParser.parseString(LibraryCodec.encodePost(post())).asJsonObject
        for(value in listOf("999999999999999999999999","1.5","\"999\"")){
            val bad=encoded.deepCopy().apply{add("score",JsonParser.parseString(value))}
            assertThrows(LibraryFailure::class.java){LibraryCodec.decodePost(bad.toString())}
        }
        assertThrows(LibraryFailure::class.java){LibraryCodec.decodePost(LibraryCodec.encodePost(post().copy(tags=listOf(Tag("bad\nname",TagCategory.UNKNOWN)))))}
    }
    @Test fun customTermsOverrideCuratedTermsAndNeverRewriteOriginalNames(){
        val curated=mapOf("character_animation" to "角色作画")
        assertEquals("用户译名",TagDictionary.resolve("character_animation",curated,mapOf("character_animation" to "用户译名")))
        assertEquals("角色作画",TagDictionary.resolve("character_animation",curated,emptyMap()))
        assertNull(TagDictionary.resolve("unknown_artist",curated,emptyMap()))
        assertTrue(LibraryCodec.tagName("original_artist"));assertFalse(LibraryCodec.tagName("original artist"))
    }
}
