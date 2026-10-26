// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalLibraryDeviceTest {
    @Test fun roomManyToManyCoverOrderingBatchMovesAndJsonMergePreserveExistingData()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val db=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val other=Room.inMemoryDatabaseBuilder(context,LibraryDatabase::class.java).build()
        val retained=File(context.cacheDir,"retained-library-media.mp4")
        instrumentation.context.assets.open("frame-index-vfr.mp4").use{input->retained.outputStream().use{input.copyTo(it)}}
        try{
            val posts=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()})
            val store=LocalLibraryStore(db);store.ready()
            val a=store.createFolder("研究 A");val b=store.createFolder("研究 B")
            store.membership(posts[0],1,true);store.membership(posts[0],a,true);store.membership(posts[1],a,true)
            store.cover(a,posts[1].id);store.reorder(a,-1);store.translation("character_animation","角色演技")
            val before=store.state.first()
            assertEquals(2,before.posts.size);assertEquals(3,before.links.size);assertEquals(a,before.folders.first().id)
            assertEquals(posts[1].id,before.folders.first{it.id==a}.coverPostId)
            val target=LocalLibraryStore(other);target.ready();target.createFolder("研究 B")
            target.translation("character_animation","保留本地译名")
            target.import(store.export());target.import(store.export())
            val merged=target.state.first()
            assertEquals(2,merged.posts.size);assertEquals(3,merged.links.size)
            assertEquals("保留本地译名",merged.translations["character_animation"])
            val mappedA=merged.folders.single{it.name=="研究 A"}.id
            assertTrue(merged.contains(posts[0].id,mappedA));assertTrue(merged.contains(posts[0].id,1))
            val corrupt=LibraryCodec.decode(store.export()).copy(links=listOf(FavoriteLink(Long.MAX_VALUE,1,0)))
            try{target.import(LibraryCodec.encode(corrupt));fail("Invalid import must fail")}catch(_:LibraryFailure){}
            assertEquals(merged.posts,target.state.first().posts)
            store.batch(setOf(posts[0].id),a,b)
            val moved=store.state.first();assertTrue(moved.contains(posts[0].id,1));assertTrue(moved.contains(posts[0].id,b));assertFalse(moved.contains(posts[0].id,a))
            store.deleteFolder(a);assertTrue(retained.isFile);assertTrue(retained.length()>0)
            val final=store.state.first();assertEquals(1,final.posts.size);assertTrue(final.contains(posts[0].id,b))
            try{store.deleteFolder(1);fail("Default folder cannot be deleted")}catch(_:LibraryFailure){}
            store.translation("character_animation",null);assertFalse(store.state.first().translations.containsKey("character_animation"))
        }finally{retained.delete();db.close();other.close()}
    }
}
