// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalLibraryUiDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun oneFavoriteEntrySupportsMultipleFoldersAndOriginalTagSearch(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val post=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        lateinit var app:AppViewModel;var searched=""
        val folderName="Device UI collection"
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        try{
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){
                Column{FavoriteControl(post,app);OriginalTag(Tag("character_animation",TagCategory.OTHER),MaterialTheme.colorScheme.onSurface,{searched=it},Modifier.testTag("original-tag-sample"))}
            }}}
            ui.waitUntil(15000){app.library.value.folders.any{it.id==1L}}
            ui.onNodeWithTag("favorite-action").performClick()
            ui.onNodeWithTag("save-default").performClick()
            ui.waitUntil(10000){app.library.value.contains(post.id,1)}
            ui.onNode(hasSetTextAction()).performTextReplacement(folderName)
            ui.onNodeWithText("New folder",substring=false).performClick()
            ui.waitUntil(10000){app.library.value.folders.firstOrNull{it.name==folderName}?.let{app.library.value.contains(post.id,it.id)}==true}
            ui.onNodeWithText("Close",substring=false).performClick()
            ui.onNodeWithTag("original-tag-sample").performClick();assertEquals("character_animation",searched)
            ui.onNodeWithTag("favorite-action").assertIsSelected()
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){LibraryScreen(app){_,_->}}}}
            val id=app.library.value.folders.single{it.name==folderName}.id
            ui.onNodeWithText(folderName,substring=false).assertExists()
            ui.onNodeWithTag("post-media-${post.id}").assertDoesNotExist()
            ui.onNode(hasSetTextAction()).assertDoesNotExist()
            UiEvidence.capture("compact-favorites-home.png")
            ui.onNodeWithTag("folder-entry-$id").performClick()
            ui.onNodeWithTag("folder-header").assertIsDisplayed()
            ui.onNodeWithTag("post-media-${post.id}").assertIsDisplayed()
            UiEvidence.capture("compact-favorites-folder.png")
            ui.onNodeWithTag("folder-back").performClick()
            ui.onNodeWithTag("folder-entry-0").assertIsDisplayed()
            ui.onNodeWithTag("post-media-${post.id}").assertDoesNotExist()
            ui.onNodeWithTag("folder-entry-0").performClick()
            ui.onNodeWithTag("post-media-${post.id}").assertIsDisplayed()
            UiEvidence.capture("phone-local-favorites.png")
            ui.onNodeWithTag("folder-back").performClick()
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){FavoriteControl(post,app)}}}
            ui.onNodeWithTag("favorite-action").performClick()
            ui.onNodeWithTag("remove-favorite").performClick()
            ui.waitUntil(10000){!app.library.value.contains(post.id)}
            ui.onNodeWithText("Close",substring=false).performClick()
            ui.onNodeWithTag("favorite-action").assertIsNotSelected()
        }finally{
            runBlocking{app.library.value.folders.firstOrNull{it.name==folderName}?.let{app.localLibrary.deleteFolder(it.id)};app.localLibrary.membership(post,1,false)}
        }
    }
}
