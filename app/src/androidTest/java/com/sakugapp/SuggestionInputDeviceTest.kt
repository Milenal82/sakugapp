// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Only this isolated test screen injects captured tag metadata; the app never fills posts with test data. */
@RunWith(AndroidJUnit4::class)
class SuggestionInputDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun imeCompositionDefersRequestsAndNewInputCancelsStaleSuggestions(){
        val calls=Collections.synchronizedList(mutableListOf<String>());val cancelled=AtomicInteger()
        var value by mutableStateOf(TextFieldValue("~gund score:>=3",TextRange(5),TextRange(1,5)))
        val options=SuggestionParser.parse(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("tag-suggestions.json").bufferedReader().use{it.readText()})
        val chosen=options.first().name
        val source=object:SuggestionSource{
            override suspend fun suggestions(prefix:String):List<TagSuggestion>{
                calls+=prefix
                if(prefix=="gund")try{delay(3000)}finally{if(!currentCoroutineContext().isActive)cancelled.incrementAndGet()}
                return options.take(1) // captured public response; only request timing is injected
            }
        }
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Column{
            val state=rememberSuggestions(value,true,source)
            OutlinedTextField(value,{value=it})
            TagSuggestionsMenu(state,false){tag->val edit=SearchEditing.complete(value.text,value.selection.start,tag);value=TextFieldValue(edit.text,TextRange(edit.cursor))}
        }}}}
        Thread.sleep(450);assertTrue("Composing input must not make a request",calls.isEmpty())
        ui.runOnUiThread{value=value.copy(composition=null)}
        ui.waitUntil(5000){calls.contains("gund")}
        ui.runOnUiThread{value=TextFieldValue("~gunda score:>=3",TextRange(6))}
        ui.waitUntil(5000){calls.contains("gunda") && cancelled.get()==1}
        ui.onNodeWithText(chosen,substring=false).performClick()
        ui.runOnUiThread{assertEquals("~$chosen score:>=3",value.text);assertEquals(chosen.length+2,value.selection.start)}
        assertEquals(listOf("gund","gunda"),calls.toList())
    }
}
