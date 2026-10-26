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
    @Test fun realPostCanBeSavedToMultipleFoldersAndCustomTranslationNeverChangesSearchTag(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val post=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        lateinit var app:AppViewModel;var searched=""
        val folderName="Device UI collection"
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        try{
            ui.runOnUiThread{ui.activity.setContent{val local by app.library.collectAsStateWithLifecycle();SakugappTheme(false){
                CompositionLocalProvider(LocalTagPresentation provides TagPresentation(true,mapOf("character_animation" to "角色作画"),local.translations,app.localLibrary)){
                    Column{FavoriteControl(post,app);TranslatedTag(Tag("character_animation",TagCategory.OTHER),MaterialTheme.colorScheme.onSurface,{searched=it},Modifier.testTag("translation-sample"))}
                }
            }}}
            ui.waitUntil(15000){app.library.value.folders.any{it.id==1L}}
            if(!app.library.value.contains(post.id,1))ui.onNodeWithTag("quick-favorite").performClick()
            ui.waitUntil(10000){app.library.value.contains(post.id,1)}
            ui.onNodeWithTag("favorite-folders").performClick()
            ui.onNode(hasSetTextAction()).performTextReplacement(folderName)
            ui.onNodeWithText("New folder",substring=false).performClick()
            ui.waitUntil(10000){app.library.value.folders.firstOrNull{it.name==folderName}?.let{app.library.value.contains(post.id,it.id)}==true}
            ui.onNodeWithText("Close",substring=false).performClick()
            ui.onNodeWithTag("translation-sample").performTouchInput{longClick()}
            ui.onNode(hasSetTextAction()).assertTextContains("角色作画",substring=true)
            ui.onNode(hasSetTextAction()).performTextReplacement("自定义作画译名")
            ui.onNodeWithText("Save local translation",substring=false).performClick()
            ui.waitUntil(10000){app.library.value.translations["character_animation"]=="自定义作画译名"}
            ui.onNodeWithText("自定义作画译名",substring=false).assertExists()
            ui.onNodeWithTag("translation-sample").performClick();assertEquals("character_animation",searched)
            ui.onNodeWithTag("translation-sample").performTouchInput{longClick()}
            ui.onNodeWithText("Remove local override",substring=false).performClick()
            ui.waitUntil(10000){"character_animation" !in app.library.value.translations}
            ui.onNodeWithTag("translation-sample").assertTextContains("角色作画",substring=true)
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){LibraryScreen(app){_,_->}}}}
            ui.onNodeWithText(folderName,substring=false).assertExists()
            UiEvidence.capture("phone-local-favorites.png")
        }finally{
            runBlocking{app.library.value.folders.firstOrNull{it.name==folderName}?.let{app.localLibrary.deleteFolder(it.id)};app.localLibrary.membership(post,1,false);app.localLibrary.translation("character_animation",null)}
        }
    }
}
