// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class CommunityUiDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun post()=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
    @Test fun guestStarGuidesToLoginAndCommentBodyStartsCollapsed(){
        val post=post();var guided=0
        ui.runOnUiThread{val app=ViewModelProvider(ui.activity)[AppViewModel::class.java];ui.activity.setContent{SakugappTheme(false){Column{VoteControl(post,app,{},{guided++});CommentsPanel(post,app,{guided++},{})}}}}
        ui.onNodeWithTag("comments-toggle").assertIsDisplayed()
        ui.onNodeWithTag("comments-content").assertDoesNotExist()
        ui.onNodeWithTag("vote-icon").performClick();ui.runOnUiThread{assertEquals(1,guided)}
        ui.onNodeWithTag("vote-1").assertDoesNotExist();ui.onNodeWithTag("comments-content").assertDoesNotExist()
    }
    @Test fun scoreRefreshPreservesFavoriteIdsTimestampsAndBothFolderRelations()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(ui.activity,LibraryDatabase::class.java).build()
        try{
            val store=LocalLibraryStore(db);val post=post();store.ready();val other=store.createFolder("Other");store.membership(post,1,true);store.membership(post,other,true)
            val child=store.createSubfolder(other,"Child");store.classify(post.id,other,child,true)
            val links=db.library().linksOnce();val before=db.library().postsOnce().single()
            store.score(post.id,77)
            val after=db.library().postsOnce().single();assertEquals(before.id,after.id);assertEquals(before.savedAt,after.savedAt);assertEquals(77,LibraryCodec.decodePost(after.snapshot).score)
            assertEquals(links,db.library().linksOnce());assertEquals(1,db.library().subfolderLinksOnce().size)
        }finally{db.close()}
    }
}
