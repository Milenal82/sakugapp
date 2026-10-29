// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupImportTest {
    private fun posts()=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).take(2)
    private fun source():LibraryBackup {
        val p=posts()
        return LibraryBackup(listOf(FavoriteFolder(91,"研究",0,p[0].id),FavoriteFolder(1,"",1),FavoriteFolder(7,"Empty",2)),
            p.mapIndexed{i,it->FavoritePost(it.id,LibraryCodec.encodePost(it),10L+i)},
            listOf(FavoriteLink(p[0].id,91,20),FavoriteLink(p[0].id,1,21),FavoriteLink(p[1].id,1,22)),
            listOf(SavedTag("original_artist","ARTIST",30)),
            listOf(TagNote("original_artist","Local meaning",40),TagNote("not_favorited","Independent\n😀",41)))
    }
    private fun legacy(value:LibraryBackup,version:Int):String {
        val gson=Gson();val o=JsonParser.parseString(LibraryCodec.encode(value)).asJsonObject
        o.remove("schemaVersion");o.addProperty("version",version)
        o.add("folders",gson.toJsonTree(value.folders));o.add("links",gson.toJsonTree(value.links))
        if(version==1){o.remove("tagFavorites");o.remove("tagNotes")}
        return o.toString()
    }
    @Test fun portableKeysDoNotDependOnAnySourceOrDestinationDatabaseId(){
        val value=source();val json=JsonParser.parseString(LibraryCodec.encode(value)).asJsonObject
        assertEquals("sakugapp.library",json["format"].asString);assertEquals(3,json["schemaVersion"].asInt)
        assertFalse(json.has("version"));assertFalse(json.toString().contains("folderId"))
        val folders=json.getAsJsonArray("folders");assertFalse(folders[0].asJsonObject.has("id"))
        val key=folders[0].asJsonObject["key"].asString
        folders[0].asJsonObject.addProperty("key","independent-key-✓")
        json.getAsJsonArray("links").forEach{if(it.asJsonObject["folderKey"].asString==key)it.asJsonObject.addProperty("folderKey","independent-key-✓")}
        val restored=LibraryCodec.decode(json.toString());val custom=restored.folders.first{it.name=="研究"}
        assertNotEquals(91L,custom.id);assertEquals(value.folders.first().coverPostId,custom.coverPostId)
        assertEquals(1,restored.links.count{it.folderId==custom.id});assertEquals(2,restored.links.count{it.folderId==1L})
        assertEquals(restored,LibraryCodec.decode("\uFEFF"+json))
    }
    @Test fun genuineVersionOneAndTwoShapesKeepOrderCoversAndManyToManyLinks(){
        val original=source()
        for(version in 1..2){
            val decoded=LibraryCodec.decode(legacy(original,version))
            assertEquals(original.folders,decoded.folders);assertEquals(original.posts,decoded.posts);assertEquals(original.links,decoded.links)
            assertEquals(if(version==2)original.notes else emptyList<TagNote>(),decoded.notes)
            assertEquals(if(version==2)original.tags else emptyList<SavedTag>(),decoded.tags)
        }
        val old=JsonParser.parseString(legacy(original,1)).asJsonObject
        old.getAsJsonArray("folders").forEach{it.asJsonObject.remove("position")}
        assertEquals(listOf(0,1,2),LibraryCodec.decode(old.toString()).folders.map{it.position})
    }
    @Test fun obsoleteLegacyCoversDoNotPreventRecoveringOtherwiseValidFolders(){
        val original=source();val stale=original.copy(folders=original.folders.map{if(it.id==91L)it.copy(coverPostId=Long.MAX_VALUE)else it})
        val old=JsonParser.parseString(legacy(original,2)).asJsonObject
        old.getAsJsonArray("folders")[0].asJsonObject.addProperty("coverPostId",Long.MAX_VALUE)
        assertNull(LibraryCodec.decode(old.toString()).folders.first{it.id==91L}.coverPostId)
        assertNull(LibraryCodec.decode(LibraryCodec.encode(stale)).folders.first{it.name=="研究"}.coverPostId)
        assertEquals(3,LibraryCodec.decode(old.toString()).links.size)
    }
    @Test fun freshRecoveryAndRepeatedMergeRestoreAllEntitiesWithoutDuplicates(){
        val incoming=LibraryCodec.decode(LibraryCodec.encode(source()))
        val empty=LibraryBackup(emptyList(),emptyList(),emptyList())
        val preview=LibraryMerge.preview(incoming,empty)
        assertEquals(BackupCounts(3,2,3,1,2),preview.counts)
        val plan=LibraryMerge.plan(incoming,empty,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(listOf("研究","","Empty"),plan.folders.sortedBy{it.position}.map{it.name})
        val restored=LibraryBackup(plan.folders,plan.posts,plan.links,plan.tags,plan.notes)
        assertEquals(2,restored.posts.size);assertEquals(3,restored.links.size);assertEquals(2,restored.notes.size)
        assertEquals(posts()[0].id,restored.folders.first{it.name=="研究"}.coverPostId)
        assertFalse(restored.tags.any{it.name=="not_favorited"});assertTrue(restored.notes.any{it.name=="not_favorited"})
        val again=LibraryMerge.plan(incoming,restored,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(0,again.result.added);assertEquals(11,again.result.skipped);assertEquals(0,again.result.failed)
        assertTrue(again.posts.isEmpty());assertTrue(again.links.isEmpty());assertTrue(again.tags.isEmpty());assertTrue(again.notes.isEmpty())
    }
    @Test fun distinctSourceKeysWithDuplicateNamesRecoverSeparatelyAndRemainIdempotent(){
        val original=source();val p=posts()
        val duplicate=original.copy(folders=original.folders+FavoriteFolder(38,"研究",3,p[1].id),links=original.links+FavoriteLink(p[1].id,38,99))
        val incoming=LibraryCodec.decode(LibraryCodec.encode(duplicate))
        val empty=LibraryBackup(emptyList(),emptyList(),emptyList())
        val first=LibraryMerge.plan(incoming,empty,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        val restored=LibraryBackup(first.folders,first.posts,first.links,first.tags,first.notes)
        val studies=restored.folders.filter{it.name=="研究"}.sortedBy{it.position}
        assertEquals(2,studies.size);assertNotEquals(studies[0].id,studies[1].id)
        assertEquals(p[0].id,studies[0].coverPostId);assertEquals(p[1].id,studies[1].coverPostId)
        assertEquals(4,restored.links.size)
        val again=LibraryMerge.plan(incoming,restored,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(0,again.result.added);assertEquals(13,again.result.skipped);assertEquals(restored.folders,again.folders)
    }
    @Test fun legacyNamedDefaultRemainsImportableUsingItsOriginalDefaultIdentity(){
        val old=JsonParser.parseString(legacy(source(),1)).asJsonObject
        old.getAsJsonArray("folders").first{it.asJsonObject["id"].asLong==1L}.asJsonObject.addProperty("name","Old default title")
        val restored=LibraryCodec.decode(old.toString())
        assertEquals("",restored.folders.single{it.id==1L}.name);assertEquals(3,restored.links.size)
    }
    @Test fun nameAndNoteConflictsAreExplicitAndDefaultMergeKeepsLocalData(){
        val incoming=LibraryCodec.decode(LibraryCodec.encode(source()))
        val old=LibraryBackup(listOf(FavoriteFolder(1,"",0),FavoriteFolder(202,"研究",7,posts()[0].id)),
            incoming.posts,listOf(FavoriteLink(posts()[0].id,202,5)),
            listOf(SavedTag("original_artist","UNKNOWN",3)),listOf(TagNote("original_artist","Existing",4)))
        val preview=LibraryMerge.preview(incoming,old)
        assertEquals(1,preview.folderConflicts);assertEquals(1,preview.noteConflicts)
        val keep=LibraryMerge.plan(incoming,old,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(7,keep.folders.single{it.id==202L}.position);assertEquals(posts()[0].id,keep.folders.single{it.id==202L}.coverPostId)
        assertFalse(keep.notes.any{it.name=="original_artist"});assertTrue(keep.notes.any{it.name=="not_favorited"})
        assertEquals("ARTIST",keep.tags.single().category)
        val replace=LibraryMerge.plan(incoming,old,FolderConflictPolicy.KEEP_BOTH,NoteConflictPolicy.REPLACE)
        assertTrue(replace.folders.any{it.name=="研究 (2)"});assertEquals("Local meaning",replace.notes.single{it.name=="original_artist"}.note)
        assertEquals("Existing",old.notes.single().note)
    }
    @Test fun malformedUnsupportedOversizedAndInvalidRelationsFailBeforeImport(){
        val raw=LibraryCodec.encode(source());val json=JsonParser.parseString(raw).asJsonObject
        val bad=listOf("{broken",raw+" trailing",raw.replace("\"format\"","format"),"[]", "null",
            json.deepCopy().apply{addProperty("schemaVersion",4)}.toString(),
            json.deepCopy().apply{getAsJsonArray("folders")[0].asJsonObject.addProperty("coverPostId",Long.MAX_VALUE)}.toString(),
            json.deepCopy().apply{getAsJsonArray("links")[0].asJsonObject.addProperty("folderKey","missing")}.toString(),
            json.deepCopy().apply{getAsJsonArray("folders").add(getAsJsonArray("folders")[0].deepCopy())}.toString())
        bad.forEach{assertThrows(it.take(60),LibraryFailure::class.java){LibraryCodec.decode(it)}}
        val future=assertThrows(LibraryFailure::class.java){LibraryCodec.decode(json.deepCopy().apply{addProperty("schemaVersion",4)}.toString())}
        assertEquals("unsupported_backup_version",future.code)
        assertEquals("library_limit",assertThrows(LibraryFailure::class.java){LibraryCodec.decode(" ".repeat(LibraryCodec.MAX_BYTES+1))}.code)
    }
}
