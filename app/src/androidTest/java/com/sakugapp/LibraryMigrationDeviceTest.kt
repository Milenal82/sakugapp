// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.sakugapp.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMigrationDeviceTest {
    @Test fun upgradingTheActualV1SchemaDropsOnlyRemovedTranslations()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val name="migration-${System.nanoTime()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JsonParser.parseString(instrumentation.context.assets.open("library-schema-v1.json").bufferedReader().use{it.readText()}).asJsonObject.getAsJsonObject("database")
        val post=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        SQLiteDatabase.openOrCreateDatabase(file,null).use{old->
            schema.getAsJsonArray("entities").forEach{entity->
                val table=entity.asJsonObject;old.execSQL(table["createSql"].asString.replace("\${TABLE_NAME}",table["tableName"].asString))
                table.getAsJsonArray("indices")?.forEach{old.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table["tableName"].asString))}
            }
            old.execSQL("INSERT INTO favorite_folders VALUES(1,'',0,NULL)")
            old.execSQL("INSERT INTO favorite_folders VALUES(8,'Keep me',1,?)",arrayOf(post.id))
            old.execSQL("INSERT INTO favorite_posts VALUES(?,?,123)",arrayOf(post.id,LibraryCodec.encodePost(post)))
            old.execSQL("INSERT INTO favorite_links VALUES(?,1,123)",arrayOf(post.id))
            old.execSQL("INSERT INTO favorite_links VALUES(?,8,124)",arrayOf(post.id))
            old.execSQL("INSERT INTO media_facts VALUES(?,1234,123)",arrayOf(post.fileUrl))
            old.execSQL("INSERT INTO tag_translations VALUES('character_animation','旧翻译')")
            old.version=1
        }
        val upgraded=Room.databaseBuilder(context,LibraryDatabase::class.java,name).addMigrations(LibraryDatabase.MIGRATION_1_2).build()
        try{
            val store=LocalLibraryStore(upgraded);val data=store.state.first()
            assertEquals(1,data.posts.size);assertEquals(post,data.decoded[post.id]);assertEquals(2,data.links.size)
            assertEquals(post.id,data.folders.single{it.id==8L}.coverPostId)
            assertEquals(1234L,data.durations[post.fileUrl]);assertTrue(data.contains(post.id,1));assertTrue(data.contains(post.id,8))
            upgraded.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name='tag_translations'").use{assertFalse(it.moveToFirst())}
            assertFalse(JsonParser.parseString(store.export()).asJsonObject.has("translations"))
            // Core writes continue to work after migration; old metadata is not recreated.
            store.rename(8,"Kept folder");assertEquals("Kept folder",store.state.first().folders.single{it.id==8L}.name)
        }finally{upgraded.close();context.deleteDatabase(name)}
    }
}
