// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.google.gson.JsonParser
import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

class PersonalDataTest {
    private fun post()=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()
    @Test fun namesUseVerifiedArtistsAndSourceFieldWithoutQueryCredentials(){
        val p=post().copy(id=42,fileExt="mp4",tags=listOf(Tag("source",TagCategory.OTHER),Tag("artist_a",TagCategory.ARTIST),Tag("artist_b",TagCategory.ARTIST),Tag("not_an_artist",TagCategory.UNKNOWN)),source="https://user:secret@www.example.com/clip/123?access_token=private#signature")
        val name=MediaFileNames.forPost(p,"mp4")
        assertEquals("artist_a__artist_b_example.com_42.mp4",name)
        assertFalse(name.contains("secret"));assertFalse(name.contains("private"));assertFalse(name.contains("not_an_artist"))
    }
    @Test fun missingPartsUnsafeCharactersUnicodeLengthAndCollisionsRemainSafe(){
        val p=post().copy(id=7,tags=emptyList(),source=null)
        assertEquals("unknown_artist_unknown_source_7.webm",MediaFileNames.forPost(p,"webm"))
        assertEquals("unknown_artist_unknown_source_7 (3).webm",MediaFileNames.unique("unknown_artist_unknown_source_7.webm"){it in setOf("unknown_artist_unknown_source_7.webm","unknown_artist_unknown_source_7 (2).webm")})
        val name=MediaFileNames.forPost(p.copy(tags=listOf(Tag("画".repeat(200)+"/\\\u0000:*?",TagCategory.ARTIST)),source="description /../../: name?token=synthetic"),"mp4")
        assertTrue(name.toByteArray().size<=MediaFileNames.MAX_BYTES);assertTrue(name.endsWith("_7.mp4"))
        assertFalse(name.contains('/'));assertFalse(name.contains('\\'));assertFalse(name.contains('\u0000'));assertFalse(name.contains("synthetic"))
        assertEquals("xn--fsqu00a.xn--0zwm56d",MediaFileNames.source("https://user:SECRET@例子.测试/private?token=PRIVATE"))
        assertFalse(MediaFileNames.source("https://example.com/auth/SECRET/session/PRIVATE/clip/12").contains("SECRET"))
        assertFalse(MediaFileNames.source("https://example.com/a/Abcdefghijklmnop0123456789XYZ").contains("Abcdef"))
        assertEquals("Reference example.com",MediaFileNames.source("Reference https://user:SECRET@example.com/private/TOKEN?auth=PRIVATE"))
        assertEquals("unknown_artist_unknown_source_7.mp4",VideoDownloadPlan.from(p.copy(fileUrl="https://www.sakugabooru.com/a.mp4",fileExt="mp4")).filename)
    }
    @Test fun explicitEpisodeMarkersProduceCanonicalNamesInOriginalArtistOrder(){
        val p=post().copy(id=123456,tags=listOf(Tag("yoshinari_you",TagCategory.ARTIST),Tag("not_artist",TagCategory.UNKNOWN)))
        for(source in listOf("ep 12","Series episode 0012","Series #12","EP_12","https://example.com/episodes/ep-12.mp4?token=SECRET")){
            assertEquals("ep_12",MediaFileNames.episode(source))
            assertEquals("yoshinari_you_ep_12_123456.mp4",MediaFileNames.forPost(p.copy(source=source),"mp4"))
        }
        assertEquals("second_artist__first_artist_ep_2_123456.webm",MediaFileNames.forPost(p.copy(source="Episode 2",tags=listOf(Tag("second_artist",TagCategory.ARTIST),Tag("first_artist",TagCategory.ARTIST))),"webm"))
        for((extension,mime)in listOf("mp4" to "video/mp4","webm" to "video/webm","mkv" to "video/x-matroska")){
            val plan=VideoDownloadPlan.from(p.copy(source="ep 12",fileUrl="https://www.sakugabooru.com/original.$extension",fileExt=extension))
            assertEquals("yoshinari_you_ep_12_123456.$extension",plan.filename);assertEquals(mime,plan.mime)
        }
    }
    @Test fun arbitraryNumbersRangesDecimalsRepeatedEpisodesAndUrlFragmentsAreNotGuessed(){
        for(source in listOf("2026 1080p scene 12","https://example.com/post/12?ep=12#12","Episode 12.5","ep 12-14","ep 12..14","ep 12 and 13","#12 / 13","ep 12 episode 13","step12","#12fps","episode 123456")){
            assertNull("Do not infer an episode from $source",MediaFileNames.episode(source))
        }
        assertEquals("unknown_artist_2026_1080p_scene_12_7.mkv",MediaFileNames.forPost(post().copy(id=7,tags=emptyList(),source="2026 1080p scene 12"),"mkv"))
    }
    @Test fun notesPreserveOriginalSearchValuesAndRemainIndependentOfSavedTags(){
        val tags=listOf(SavedTag("original_tag","ARTIST",10),SavedTag("other_tag","UNKNOWN",20))
        val notes=listOf(TagNote("original_tag","My personal alias\n心得",30),TagNote("unfavorited","Independent",31))
        assertEquals("original_tag",PersonalRules.searchable(tags,notes,"personal",0).single().name)
        assertEquals(2,PersonalRules.searchable(tags,notes,"tag",0).size)
        assertEquals(listOf("other_tag","original_tag"),PersonalRules.searchable(tags,notes,"",0).map{it.name})
        assertEquals("a\nb c",PersonalRules.note(" \r\na\r\nb\tc\u0000 "))
        assertThrows(LibraryFailure::class.java){PersonalRules.note("x".repeat(501))}
        assertThrows(LibraryFailure::class.java){PersonalRules.note((1..9).joinToString("\n"){"line"})}
        assertEquals(500,PersonalRules.note("😀".repeat(500)).codePointCount(0,1000))
        assertEquals("original_tag",PersonalRules.queryTags("original_tag score:>=3",listOf(post())).single().name)
        assertTrue(PersonalRules.queryTags("score:>=3 tag*",listOf(post())).isEmpty())
        assertEquals(TagCategory.UNKNOWN,PersonalRules.queryTags("unknown",listOf(post())).single().category)
    }
    @Test fun historyDeduplicationCapacityAndByteBudgetDiscardOldestNotPersonalRecords(){
        val rows=(1L..510L).map{ViewedPost(it,"snapshot",it)}
        val kept=PersonalRules.historyKeep(rows+ViewedPost(1,"new snapshot",999))
        assertEquals(500,kept.size);assertEquals(1L,kept.first().id);assertEquals(500,kept.map{it.id}.distinct().size)
        assertFalse(kept.any{it.id==2L})
        assertTrue(PersonalRules.historyKeep(listOf(ViewedPost(1,"x".repeat(PersonalRules.HISTORY_BYTES+1),1))).isEmpty())
    }
    @Test fun portableBackupsRoundTripUnfavoritedNotesAndNeverExportHistory(){
        val backup=LibraryBackup(emptyList(),emptyList(),emptyList(),listOf(SavedTag("original_tag","ARTIST",1)),listOf(TagNote("not_saved","中文\n😀",2)))
        val json=LibraryCodec.encode(backup);val parsed=JsonParser.parseString(json).asJsonObject
        assertEquals(3,parsed["schemaVersion"].asInt);assertFalse(parsed.has("history"));assertFalse(parsed.has("credentials"))
        assertEquals(backup,LibraryCodec.decode(json))
        val future=parsed.deepCopy().apply{addProperty("schemaVersion",4)}
        assertThrows(LibraryFailure::class.java){LibraryCodec.decode(future.toString())}
        val invalid=parsed.deepCopy().apply{getAsJsonArray("tagNotes")[0].asJsonObject.addProperty("name","bad tag")}
        assertThrows(LibraryFailure::class.java){LibraryCodec.decode(invalid.toString())}
    }
    @Test fun actualVersionOneShapeRemainsImportableWithoutNewFields(){
        val post=post();val value=LibraryBackup(listOf(FavoriteFolder(1,"",0)),listOf(FavoritePost(post.id,LibraryCodec.encodePost(post),1)),listOf(FavoriteLink(post.id,1,1)))
        val old=JsonParser.parseString(LibraryCodec.encode(value)).asJsonObject.apply{
            remove("schemaVersion");addProperty("version",1);remove("tagFavorites");remove("tagNotes")
            val gson=com.google.gson.Gson();add("folders",gson.toJsonTree(value.folders));add("links",gson.toJsonTree(value.links))
        }
        assertEquals(value,LibraryCodec.decode(old.toString()))
    }
    @Test fun allNewLanguagesAreNativeOnlyAndSixColumnsRemainAPhoneOption(){
        for(language in listOf("fr","es","ru","ko")){
            assertEquals(language,PreferenceRules.resolveLanguage("system","$language-FR"))
            assertEquals(language,PreferenceRules.platformLanguage(language));assertEquals("en",PreferenceRules.siteLanguage(language))
        }
        assertTrue(6 in PreferenceRules.columnOptions)
    }
}
