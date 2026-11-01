// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HierarchyDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun post()=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
    @Test fun actualV3MigrationRetainsAllDataAndClassificationsCascadeOnlyWithinTheirParent()=runBlocking{
        val context=ui.activity;val name="hierarchy-${System.nanoTime()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JsonParser.parseString(instrumentation.context.assets.open("library-schema-v3.json").bufferedReader().use{it.readText()}).asJsonObject.getAsJsonObject("database")
        val p=post()
        SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
            schema.getAsJsonArray("entities").forEach{entry->val table=entry.asJsonObject;val tableName=table["tableName"].asString
                db.execSQL(table["createSql"].asString.replace("\${TABLE_NAME}",tableName))
                table.getAsJsonArray("indices")?.forEach{db.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",tableName))}
            }
            db.execSQL("INSERT INTO favorite_folders VALUES(1,'',1,NULL)")
            db.execSQL("INSERT INTO favorite_folders VALUES(71,'Keep',0,?)",arrayOf(p.id))
            db.execSQL("INSERT INTO favorite_posts VALUES(?,?,123)",arrayOf(p.id,LibraryCodec.encodePost(p)))
            db.execSQL("INSERT INTO favorite_links VALUES(?,1,124)",arrayOf(p.id));db.execSQL("INSERT INTO favorite_links VALUES(?,71,125)",arrayOf(p.id))
            db.execSQL("INSERT INTO media_facts VALUES(?,1234,126)",arrayOf(p.fileUrl))
            db.execSQL("INSERT INTO saved_tags VALUES('mokmok','ARTIST',127)")
            db.execSQL("INSERT INTO tag_notes VALUES('standalone','Local note',128)")
            db.execSQL("INSERT INTO viewed_posts VALUES(?,?,129)",arrayOf(p.id,LibraryCodec.encodePost(p)))
            db.execSQL("INSERT INTO history_policy VALUES(1,0)");db.version=3
        }
        var db=Room.databaseBuilder(context,LibraryDatabase::class.java,name).addMigrations(LibraryDatabase.MIGRATION_3_4).build()
        var restore:LibraryDatabase?=null
        try{
            var store=LocalLibraryStore(db);val original=store.state.first()
            assertEquals(listOf(71L,1L),original.folders.map{it.id});assertEquals(2,original.links.size)
            assertEquals(123L,original.posts.single().savedAt);assertEquals(p.id,original.folders.first().coverPostId)
            assertEquals(1234L,original.durations[p.fileUrl]);assertEquals("Local note",db.personal().notesOnce().single().note)
            assertEquals(1,db.personal().historyOnce().size);assertFalse(PersonalStore(db).recording.first())
            val a=store.createSubfolder(71,"Action");val b=store.createSubfolder(71,"Effects")
            store.classify(p.id,71,a,true);store.classify(p.id,71,b,true)
            store.orderFolders(listOf(1,71));store.orderSubfolders(71,listOf(b,a));store.renameSubfolder(a,"Renamed")
            val raw=store.export();db.close();db=Room.databaseBuilder(context,LibraryDatabase::class.java,name).build();store=LocalLibraryStore(db)
            assertEquals(listOf(1L,71L),store.state.first().folders.map{it.id});assertEquals(listOf(b,a),db.library().subfoldersOnce().map{it.id})
            restore=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build();val fresh=LocalLibraryStore(restore)
            fresh.import(raw);val saved=fresh.state.first();assertEquals(2,saved.subfolderLinks.size);assertEquals(2,saved.links.size)
            assertEquals(listOf("Effects","Renamed"),saved.subfolders.map{it.name});assertEquals(0,fresh.import(raw).added)
            assertTrue(restore.personal().historyOnce().isEmpty());assertEquals("Local note",restore.personal().notesOnce().single().note)
            val before=store.export();try{store.import(raw.replace("\"schemaVersion\":4","\"schemaVersion\":999"));fail("Reject unsupported backup")}catch(_:LibraryFailure){}
            assertEquals(before,store.export())
            try{store.classify(p.id,1,a,true);fail("Reject a different parent")}catch(_:LibraryFailure){}
            store.deleteSubfolder(a);assertEquals(1,db.library().subfolderLinksOnce().size);assertEquals(2,db.library().linksOnce().size)
            store.membership(p,71,false);assertTrue(db.library().subfolderLinksOnce().isEmpty());assertTrue(store.state.first().contains(p.id,1))
            val c=store.createSubfolder(71,"Empty");assertTrue(c>0);store.deleteFolder(71)
            assertTrue(db.library().subfoldersOnce().isEmpty());assertEquals(1,db.library().postsOnce().size)
        }finally{restore?.close();db.close();context.deleteDatabase(name)}
    }
    @Test fun dragOrderingNestedManagementPrimaryOnlySelectorAndAccentPersistence(){
        lateinit var app:AppViewModel;val p=post()
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        val previous=app.settings.value;val roots=runBlocking{app.localLibrary.ready();val a=app.localLibrary.createFolder("Hierarchy A");val b=app.localLibrary.createFolder("Hierarchy B");app.localLibrary.membership(p,a,true);listOf(a,b)}
        ui.waitUntil(10000){app.library.value.folders.any{it.id==roots[1]}}
        var page by mutableIntStateOf(0)
        ui.runOnUiThread{ui.activity.setContent{
            val s by app.settings.collectAsState();SakugappTheme(themeIsLight(s.themeMode,false),s.fontScale,s.accent){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                when(page){0->LibraryScreen(app){_,_->};1->SettingsScreen(s,app,{},{});else->FavoriteControl(p,app)}
            }}
        }}
        try{
            ui.onNodeWithTag("order-folders").performScrollTo().performClick()
            val from=ui.onNodeWithTag("order-handle-${roots[1]}");val dy=ui.onNodeWithTag("order-row-${roots[0]}").fetchSemanticsNode().boundsInRoot.center.y-from.fetchSemanticsNode().boundsInRoot.center.y
            from.performTouchInput{down(center)};ui.mainClock.advanceTimeBy(700)
            from.performTouchInput{moveBy(Offset(0f,dy),400);up()}
            ui.waitUntil(10000){app.library.value.folders.indexOfFirst{it.id==roots[1]}<app.library.value.folders.indexOfFirst{it.id==roots[0]}}
            UiEvidence.capture("v121-folder-order.png")
            ui.onNodeWithText(ui.activity.getString(R.string.ui_close)).performClick()
            ui.onNodeWithTag("folder-entry-${roots[0]}").performScrollTo().performClick()
            ui.onNodeWithTag("new-subfolder").performClick();ui.onNodeWithTag("subfolder-name").performTextInput("Action")
            ui.onNodeWithTag("confirm-subfolder").performClick();ui.waitUntil(10000){app.library.value.subfolders.any{it.name=="Action"}}
            val a=app.library.value.subfolders.single{it.name=="Action"}.id
            val b=runBlocking{app.localLibrary.createSubfolder(roots[0],"Daily")}
            ui.waitUntil(10000){app.library.value.subfolders.any{it.id==b}}
            ui.onNodeWithTag("subfolder-menu-$a").performScrollTo().performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_rename)).performClick()
            ui.onNodeWithTag("subfolder-name").performTextReplacement("Effects");ui.onNodeWithTag("confirm-subfolder").performClick()
            ui.onNodeWithTag("post-media-${p.id}").performScrollTo().performTouchInput{longClick()}
            ui.onNodeWithText(ui.activity.getString(R.string.ui_add_to_subfolder)).performClick()
            ui.onNodeWithTag("classify-$a").performClick();ui.onNodeWithTag("classify-$b").performClick()
            ui.waitUntil(10000){app.library.value.classified(p.id,a) && app.library.value.classified(p.id,b)}
            ui.onNodeWithText(ui.activity.getString(R.string.ui_close)).performClick()
            ui.onNodeWithTag("subfolder-entry-$a").performScrollTo().performClick()
            ui.onNodeWithTag("new-subfolder").assertDoesNotExist();ui.onNodeWithTag("post-media-${p.id}").assertExists()
            ui.onNodeWithTag("folder-back").performClick()
            ui.onNodeWithTag("subfolder-entry-$a").performScrollTo();UiEvidence.capture("v121-subfolders.png")
            ui.onNodeWithTag("subfolder-menu-$a").performScrollTo().performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_delete_subfolder)).performClick()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_delete)).performClick()
            ui.waitUntil(10000){app.library.value.subfolders.none{it.id==a}}
            assertTrue(app.library.value.contains(p.id,roots[0]));assertTrue(app.library.value.classified(p.id,b))
            ui.runOnUiThread{page=1}
            for((accent,mode)in listOf("blue" to "dark","pink" to "light")){
                ui.runOnUiThread{app.theme(mode)}
                ui.onNodeWithTag("accent-$accent").performScrollTo().performClick()
                ui.waitUntil(10000){app.settings.value.accent==accent}
                val persisted=runBlocking{PreferencesStore(ui.activity).settings.first()}
                assertEquals(accent,persisted.accent);assertEquals(previous.fontScale,persisted.fontScale,0f);assertEquals(previous.tagVerticalSpacing,persisted.tagVerticalSpacing,0f)
                UiEvidence.capture("v121-$accent-$mode.png")
            }
            ui.runOnUiThread{page=2};ui.onNodeWithTag("favorite-action").performClick()
            assertEquals(app.library.value.folders.size,ui.onAllNodes(hasTestTagPrefix("favorite-folder-")).fetchSemanticsNodes().size)
            ui.onNodeWithText("Daily").assertDoesNotExist();UiEvidence.capture("v121-favorites-primary.png")
        }finally{runBlocking{roots.forEach{app.localLibrary.deleteFolder(it)};app.preferences.accent(previous.accent);app.preferences.theme(previous.themeMode)}}
    }
    private fun hasTestTagPrefix(prefix:String)=SemanticsMatcher("tag starts with $prefix"){it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}.startsWith(prefix)}
}
