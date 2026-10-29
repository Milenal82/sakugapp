// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalStoreDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun posts()=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()})
    @Test fun historyReopensDeduplicatesAndDisabledRecordingNeverErasesOtherLocalData()=runBlocking{
        val name="personal-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,LibraryDatabase::class.java,name).build()
        try{
            var time=10L;var store=PersonalStore(db){time++};val p=posts()
            val library=LocalLibraryStore(db);library.ready();library.membership(p[0],1,true)
            store.saveTag(Tag("mokmok",TagCategory.UNKNOWN));store.learn(listOf(Tag("mokmok",TagCategory.ARTIST),Tag("not_saved",TagCategory.ARTIST)));assertEquals("ARTIST",store.tags.first().single().category);store.note("independent_tag","Local understanding\n😀")
            store.record(p[0]);store.record(p[1]);store.record(p[0])
            assertEquals(listOf(p[0].id,p[1].id),store.history.first().map{it.id})
            store.removeHistory(p[1].id);assertEquals(listOf(p[0].id),store.history.first().map{it.id});store.record(p[1]);store.record(p[0])
            store.recording(false);repeat(4){store.record(p[1])};assertEquals(p[0].id,store.history.first().first().id)
            db.close();db=Room.databaseBuilder(context,LibraryDatabase::class.java,name).build();store=PersonalStore(db)
            assertFalse(store.recording.first());assertEquals(2,store.history.first().size)
            store.record(p[1]);assertEquals(p[0].id,store.history.first().first().id)
            store.removeTag("mokmok");assertEquals("Local understanding\n😀",store.notes.first().single().note)
            store.saveTag(Tag("independent_tag",TagCategory.UNKNOWN));store.note("independent_tag","")
            assertTrue(store.tags.first().any{it.name=="independent_tag"});assertTrue(store.notes.first().isEmpty())
            store.clearHistory();assertTrue(store.history.first().isEmpty())
            assertEquals(1,LocalLibraryStore(db).state.first().posts.size);assertEquals(1,store.tags.first().size)
        }finally{db.close();context.deleteDatabase(name)}
    }
    @Test fun capacityEvictsOldestAndPortableBackupsMergeNotesIndependentlyWithoutHistory()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val targetDb=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        try{
            val p=posts().first();val store=PersonalStore(db){1000};val dao=db.personal();val library=LocalLibraryStore(db)
            db.withTransaction{(1L..501L).forEach{dao.viewed(ViewedPost(it,LibraryCodec.encodePost(p.copy(id=it)),it))}}
            store.record(p);assertEquals(500,store.history.first().size);assertEquals(p.id,store.history.first().first().id)
            assertFalse(store.history.first().any{it.id==1L})
            store.saveTag(Tag("mokmok",TagCategory.ARTIST));store.note("unfavorited_note","Personal note")
            library.ready();library.membership(p,1,true)
            val backup=library.export();val json=JsonParser.parseString(backup).asJsonObject
            assertEquals(3,json["schemaVersion"].asInt);assertFalse(json.has("history"))
            val target=LocalLibraryStore(targetDb);val personal=PersonalStore(targetDb)
            personal.note("unfavorited_note","Keep existing note");personal.record(p)
            target.import(backup);target.import(backup)
            assertEquals(1,personal.tags.first().size);assertEquals("Keep existing note",personal.notes.first().single().note)
            assertEquals(1,personal.history.first().size)
            assertEquals(1,target.state.first().posts.size)
            val legacy=json.deepCopy().apply{
                remove("schemaVersion");addProperty("version",1);remove("tagFavorites");remove("tagNotes")
                val gson=com.google.gson.Gson();add("folders",gson.toJsonTree(db.library().foldersOnce()));add("links",gson.toJsonTree(db.library().linksOnce()))
            }
            target.import(legacy.toString());assertEquals(1,personal.tags.first().size)
            val invalid=json.deepCopy().apply{getAsJsonArray("tagNotes")[0].asJsonObject.addProperty("note","x".repeat(501))}
            try{target.import(invalid.toString());fail("Invalid note import must fail atomically")}catch(_:LibraryFailure){}
            assertEquals("Keep existing note",personal.notes.first().single().note);assertEquals(1,target.state.first().posts.size)
        }finally{db.close();targetDb.close()}
    }
    @Test fun actualV2DatabaseMigratesWithoutChangingFoldersMediaOrPostRelationships()=runBlocking{
        val name="migration-v2-${System.nanoTime()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JsonParser.parseString(InstrumentationRegistry.getInstrumentation().context.assets.open("library-schema-v2.json").bufferedReader().use{it.readText()}).asJsonObject.getAsJsonObject("database")
        val p=posts().first()
        SQLiteDatabase.openOrCreateDatabase(file,null).use{old->
            schema.getAsJsonArray("entities").forEach{e->val table=e.asJsonObject;old.execSQL(table["createSql"].asString.replace("\${TABLE_NAME}",table["tableName"].asString));table.getAsJsonArray("indices")?.forEach{old.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table["tableName"].asString))}}
            old.execSQL("INSERT INTO favorite_folders VALUES(1,'',0,NULL)")
            old.execSQL("INSERT INTO favorite_folders VALUES(4,'Old folder',1,?)",arrayOf(p.id))
            old.execSQL("INSERT INTO favorite_posts VALUES(?,?,123)",arrayOf(p.id,LibraryCodec.encodePost(p)))
            old.execSQL("INSERT INTO favorite_links VALUES(?,1,123)",arrayOf(p.id));old.execSQL("INSERT INTO favorite_links VALUES(?,4,124)",arrayOf(p.id))
            old.execSQL("INSERT INTO media_facts VALUES(?,5432,125)",arrayOf(p.fileUrl));old.version=2
        }
        val db=Room.databaseBuilder(context,LibraryDatabase::class.java,name).addMigrations(LibraryDatabase.MIGRATION_2_3).build()
        try{
            val state=LocalLibraryStore(db).state.first();assertEquals(p,state.decoded[p.id]);assertEquals(2,state.links.size)
            assertEquals(p.id,state.folders.single{it.id==4L}.coverPostId);assertEquals(5432L,state.durations[p.fileUrl]);assertEquals(123L,state.posts.single().savedAt)
            val store=PersonalStore(db);assertTrue(store.tags.first().isEmpty());assertTrue(store.notes.first().isEmpty());assertTrue(store.history.first().isEmpty())
            store.saveTag(Tag("mokmok",TagCategory.UNKNOWN));store.record(p);assertEquals(1,store.history.first().size)
        }finally{db.close();context.deleteDatabase(name)}
    }
}
