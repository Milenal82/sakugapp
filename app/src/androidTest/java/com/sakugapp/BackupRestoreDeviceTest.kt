// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sakugapp.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Focused data-integrity tests: no network, account, player or large UI suite. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun posts()=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).take(2)
    private suspend fun populate(db:LibraryDatabase):String {
        val store=LocalLibraryStore(db);val p=posts();store.ready()
        db.library().folder(FavoriteFolder(91,"Study",0));db.library().folder(FavoriteFolder(7,"Empty",1));db.library().position(1,2)
        store.membership(p[0],91,true);store.membership(p[0],1,true);store.membership(p[1],91,true)
        store.cover(91,p[1].id);store.cover(1,p[0].id)
        val personal=PersonalStore(db);personal.saveTag(Tag("mokmok",TagCategory.ARTIST));personal.saveTag(Tag("unknown_category",TagCategory.UNKNOWN))
        personal.note("mokmok","Saved tag note");personal.note("not_favorited","Independent note\n😀");personal.record(p[0])
        store.duration(p[0].fileUrl!!,7654)
        return store.export()
    }
    private suspend fun backup(db:LibraryDatabase)=LibraryBackup(db.library().foldersOnce(),db.library().postsOnce(),db.library().linksOnce(),db.personal().tagsOnce(),db.personal().notesOnce())
    @Test fun exportedFileRestoresFreshDatabaseAndReopensWithEveryFolderRelationshipAndStandaloneNote()=runBlocking {
        val source=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val name="fresh-restore-${System.nanoTime()}.db"
        val raw=try{populate(source)}finally{source.close()}
        var target=Room.databaseBuilder(context,LibraryDatabase::class.java,name).build()
        try {
            assertTrue(target.library().foldersOnce().isEmpty());assertTrue(target.personal().notesOnce().isEmpty())
            val store=LocalLibraryStore(target);val preview=store.preview(raw)
            assertEquals(BackupCounts(3,2,3,2,2),preview.counts);assertEquals(0,preview.folderConflicts);assertEquals(0,preview.noteConflicts)
            val result=store.import(preview);assertEquals(11,result.added);assertEquals(1,result.skipped);assertEquals(0,result.failed)
            val saved=store.state.first();val study=saved.folders.single{it.name=="Study"}
            assertNotEquals(91L,study.id);assertEquals(listOf("Study","Empty",""),saved.folders.map{it.name})
            assertEquals(2,saved.posts.size);assertEquals(3,saved.links.size);assertEquals(posts()[1].id,study.coverPostId)
            assertEquals(posts()[0].id,saved.folders.single{it.id==1L}.coverPostId)
            assertTrue(saved.contains(posts()[0].id,study.id));assertTrue(saved.contains(posts()[0].id,1))
            assertTrue(saved.inFolder(saved.folders.single{it.name=="Empty"}.id).isEmpty())
            assertEquals(setOf("mokmok","unknown_category"),target.personal().tagsOnce().map{it.name}.toSet())
            assertEquals("ARTIST",target.personal().tagsOnce().single{it.name=="mokmok"}.category)
            assertEquals("UNKNOWN",target.personal().tagsOnce().single{it.name=="unknown_category"}.category)
            assertEquals("Independent note\n😀",target.personal().notesOnce().single{it.name=="not_favorited"}.note)
            assertTrue(target.personal().historyOnce().isEmpty());assertTrue(saved.durations.isEmpty())
            val again=store.import(raw);assertEquals(0,again.added);assertEquals(12,again.skipped)
            val before=backup(target);target.close();target=Room.databaseBuilder(context,LibraryDatabase::class.java,name).build()
            assertEquals(before,backup(target))
        } finally {target.close();context.deleteDatabase(name)}
    }
    @Test fun freshRecoveryKeepsDistinctSameNameFoldersCoversAndMemberships()=runBlocking {
        val source=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        try {
            populate(source)
            source.library().folder(FavoriteFolder(38,"Study",3))
            val store=LocalLibraryStore(source);store.membership(posts()[0],38,true);store.cover(38,posts()[0].id)
            val raw=store.export();val restore=LocalLibraryStore(target)
            restore.import(raw)
            val state=restore.state.first();val studies=state.folders.filter{it.name=="Study"}.sortedBy{it.position}
            assertEquals(2,studies.size);assertNotEquals(studies[0].id,studies[1].id)
            assertEquals(posts()[1].id,studies[0].coverPostId);assertEquals(posts()[0].id,studies[1].coverPostId)
            assertEquals(2,state.links.count{it.folderId==studies[0].id});assertEquals(1,state.links.count{it.folderId==studies[1].id})
            assertEquals(0,restore.import(raw).added);assertEquals(state,restore.state.first())
        } finally {source.close();target.close()}
    }
    @Test fun oldFilesAndConflictsMergeWithoutChangingExistingNotesMediaHistoryOrPostIdentity()=runBlocking {
        val source=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        try {
            val raw=populate(source);val sourceValue=backup(source);val store=LocalLibraryStore(target);store.ready()
            val local=store.createFolder("study");store.membership(posts()[0],local,true);store.cover(local,posts()[0].id)
            val personal=PersonalStore(target);personal.note("mokmok","Keep existing");personal.record(posts()[1]);store.duration(posts()[0].fileUrl!!,4321)
            val preview=store.preview(raw);assertEquals(1,preview.folderConflicts);assertEquals(1,preview.noteConflicts)
            store.import(preview)
            assertEquals("Keep existing",target.personal().notesOnce().single{it.name=="mokmok"}.note)
            assertEquals(posts()[0].id,store.state.first().folders.single{it.id==local}.coverPostId)
            val localPost=target.library().postsOnce().single{it.id==posts()[0].id}
            for(version in 1..2){
                val old=JsonParser.parseString(raw).asJsonObject.apply{
                    remove("schemaVersion");addProperty("version",version)
                    add("folders",Gson().toJsonTree(sourceValue.folders));add("links",Gson().toJsonTree(sourceValue.links))
                    if(version==1){remove("tagFavorites");remove("tagNotes")}
                }
                store.import(old.toString())
            }
            assertEquals(3,target.library().linksOnce().size);assertEquals(2,target.library().postsOnce().size)
            assertEquals(localPost,target.library().postsOnce().single{it.id==posts()[0].id})
            assertEquals(4321L,store.state.first().durations[posts()[0].fileUrl]);assertEquals(1,target.personal().historyOnce().size)
            assertEquals("Keep existing",target.personal().notesOnce().single{it.name=="mokmok"}.note)
            store.import(preview,notePolicy=NoteConflictPolicy.REPLACE)
            assertEquals("Saved tag note",target.personal().notesOnce().single{it.name=="mokmok"}.note)
            assertEquals(2,target.personal().notesOnce().size)
        } finally {source.close();target.close()}
    }
    @Test fun corruptUnsupportedAndCapacityFailuresLeaveAllLocalTablesUnchanged()=runBlocking {
        val source=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        try {
            val raw=populate(source);val store=LocalLibraryStore(target);store.ready()
            store.membership(posts()[0],1,true);val personal=PersonalStore(target);personal.note("mokmok","Keep");personal.record(posts()[1]);store.duration(posts()[0].fileUrl!!,1234)
            val before=backup(target);val history=target.personal().historyOnce();val durations=store.state.first().durations
            val json=JsonParser.parseString(raw).asJsonObject
            val invalid=listOf("{broken",json.deepCopy().apply{addProperty("schemaVersion",999)}.toString(),
                json.deepCopy().apply{getAsJsonArray("links")[0].asJsonObject.addProperty("postId",Long.MAX_VALUE)}.toString(),
                json.deepCopy().apply{getAsJsonArray("tagNotes")[1].asJsonObject.addProperty("note","x".repeat(501))}.toString())
            for(body in invalid){try{store.import(body);fail("Invalid data must not import")}catch(_:LibraryFailure){}
                assertEquals(before,backup(target));assertEquals(history,target.personal().historyOnce());assertEquals(durations,store.state.first().durations)
            }
            repeat(99){target.library().folder(FavoriteFolder(name="Existing $it",position=it+1))}
            val full=backup(target)
            try{store.import(store.preview(raw),notePolicy=NoteConflictPolicy.REPLACE);fail("Over-capacity import must roll back")}catch(error:LibraryFailure){assertEquals("library_limit",error.code)}
            assertEquals(full,backup(target));assertEquals(history,target.personal().historyOnce());assertEquals(durations,store.state.first().durations)
        } finally {source.close();target.close()}
    }
}
