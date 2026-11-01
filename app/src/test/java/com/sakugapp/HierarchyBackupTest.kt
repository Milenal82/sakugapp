// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.google.gson.JsonParser
import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

class HierarchyBackupTest {
    @Test fun officialUpgradeSeedIsAValidV3BackupOfTheCapturedPosts(){
        val relative="upgrade-ui-check/fixtures/official-v120-favorites.json"
        val file=java.io.File(relative).takeIf{it.isFile} ?: java.io.File("../$relative")
        val value=LibraryCodec.decode(file.readText())
        assertEquals(3,value.folders.size);assertEquals(2,value.posts.size);assertEquals(3,value.links.size)
        assertEquals(setOf("mokmok","genga"),value.notes.map{it.name}.toSet())
        val captured=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText())
        assertEquals(captured.map{it.id}.toSet(),value.posts.map{it.id}.toSet())
        captured.forEach{post->val saved=LibraryCodec.decodePost(value.posts.single{it.id==post.id}.snapshot);assertEquals(post.score,saved.score);assertEquals(post.fileUrl,saved.fileUrl);assertEquals(post.source,saved.source)}
    }
    private fun source():LibraryBackup {
        val p=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()
        return LibraryBackup(listOf(FavoriteFolder(1,"",1),FavoriteFolder(71,"Animation",0,p.id)),listOf(FavoritePost(p.id,LibraryCodec.encodePost(p),123)),
            listOf(FavoriteLink(p.id,1,124),FavoriteLink(p.id,71,125)),listOf(SavedTag("mokmok","ARTIST",126)),listOf(TagNote("note_only","Keep me",127)),
            listOf(FavoriteSubfolder(91,71,"Action",0),FavoriteSubfolder(92,71,"Effects",1)),
            listOf(FavoriteSubfolderLink(p.id,91,71),FavoriteSubfolderLink(p.id,92,71)))
    }
    @Test fun portableHierarchyRestoresIntoAnEmptyInstallAndRepeatedImportIsIdempotent(){
        val value=source();val body=LibraryCodec.encode(value)
        assertFalse(body.contains("folderId"));assertFalse(body.contains("subfolderId"))
        val restored=LibraryCodec.decode(body)
        val plan=LibraryMerge.plan(restored,LibraryBackup(emptyList(),emptyList(),emptyList()),FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(2,plan.folders.size);assertEquals(1,plan.posts.size);assertEquals(2,plan.links.size)
        assertEquals(listOf("Action","Effects"),plan.subfolders.map{it.name});assertEquals(2,plan.subfolderLinks.size)
        assertEquals(plan.folders.single{it.name=="Animation"}.id,plan.subfolderLinks.first().folderId)
        val loaded=LibraryBackup(plan.folders,plan.posts,plan.links,plan.tags,plan.notes,plan.subfolders,plan.subfolderLinks)
        val again=LibraryMerge.plan(restored,loaded,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(0,again.result.added);assertTrue(again.subfolderLinks.isEmpty());assertTrue(again.subfolders.isEmpty())
        assertEquals(value.notes,loaded.notes)
    }
    @Test fun rootAndSubfolderIdsAreRemappedAndLocalOrderAndNotesAreKept(){
        val source=LibraryCodec.decode(LibraryCodec.encode(source()));val post=source.posts.single()
        val old=LibraryBackup(listOf(FavoriteFolder(1,"",0),FavoriteFolder(510,"Animation",7,post.id)),listOf(post),listOf(FavoriteLink(post.id,510,10)),
            notes=listOf(TagNote("note_only","Local note",999)),subfolders=listOf(FavoriteSubfolder(72,510,"Action",5)))
        val plan=LibraryMerge.plan(source,old,FolderConflictPolicy.MERGE_BY_NAME,NoteConflictPolicy.KEEP_EXISTING)
        assertEquals(7,plan.folders.single{it.id==510L}.position)
        assertEquals("Effects",plan.subfolders.single().name);assertEquals(510L,plan.subfolders.single().folderId)
        assertTrue(plan.subfolderLinks.any{it.subfolderId==72L && it.folderId==510L});assertTrue(plan.notes.isEmpty())
    }
    @Test fun v3StillLoadsAndBrokenClassificationsAreRejectedBeforeWriting(){
        val json=JsonParser.parseString(LibraryCodec.encode(source())).asJsonObject
        val old=json.deepCopy().apply{addProperty("schemaVersion",3);remove("subfolders");remove("subfolderLinks")}
        assertEquals(2,LibraryCodec.decode(old.toString()).links.size);assertTrue(LibraryCodec.decode(old.toString()).subfolders.isEmpty())
        val invalid=listOf(
            json.deepCopy().apply{getAsJsonArray("subfolderLinks")[0].asJsonObject.addProperty("postId",Long.MAX_VALUE)},
            json.deepCopy().apply{getAsJsonArray("subfolders")[0].asJsonObject.addProperty("folderKey","subfolder_1")},
            json.deepCopy().apply{getAsJsonArray("links").remove(1)},
            json.deepCopy().apply{getAsJsonArray("subfolders")[1].asJsonObject.addProperty("name","Action")})
        invalid.forEach{assertThrows(LibraryFailure::class.java){LibraryCodec.decode(it.toString())}}
    }
    @Test fun appendingPagesKeepsUnchangedCardObjectsButRepairsNewlyKnownCategories(){
        val p=LibraryCodec.decodePost(source().posts.first().snapshot)
        val first=FeedState(posts=listOf(p))
        assertSame(p,first.append(PostPage(emptyList(),0),60).posts.single())
        val tag=p.tags.first();val category=if(tag.category==TagCategory.ARTIST)TagCategory.COPYRIGHT else TagCategory.ARTIST
        val changed=first.append(PostPage(emptyList(),0,classifications=mapOf(tag.name to category)),60).posts.single()
        assertNotSame(p,changed);assertEquals(category,changed.tags.first().category)
        assertSame(changed,first.copy(posts=listOf(changed)).append(PostPage(emptyList(),0,classifications=mapOf(tag.name to category)),60).posts.single())
    }
}
