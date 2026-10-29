// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.*
import com.sakugapp.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Real app navigation on a fresh narrow AVD; recorded posts come from public-response fixtures. */
@RunWith(AndroidJUnit4::class)
class MaintenanceUiDeviceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    companion object {
        private fun device() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        @JvmStatic @BeforeClass fun setFontBeforeActivity() {
            device().executeShellCommand("settings put system font_scale 1.3")
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        @JvmStatic @AfterClass fun restoreFont() {
            device().executeShellCommand("settings put system font_scale 1.0")
        }
    }

    private fun menu(label: Int) {
        ui.onNodeWithText(ui.activity.getString(R.string.ui_menu), substring = false).performClick()
        ui.onNodeWithText(ui.activity.getString(label), substring = false).performScrollTo().performClick()
    }

    private fun hideIme() {
        ui.runOnUiThread {
            (ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(ui.activity.window.decorView.windowToken, 0)
            ui.activity.window.decorView.clearFocus()
        }
        ui.waitForIdle()
    }

    private fun readableTool(tag: String, label: Int) {
        val node = ui.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("$tag stays inside the physical viewport", node.boundsInRoot.width <= device().displayWidth + 1)
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNode(hasText(ui.activity.getString(label), substring = false) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$tag has a real text layout", layouts.isNotEmpty())
        for (layout in layouts) {
            println("$tag text=${layout.layoutInput.text} size=${layout.size} lines=${layout.lineCount} overflow=${layout.hasVisualOverflow}")
            assertFalse("$tag has enough height", layout.didOverflowHeight)
            assertTrue("$tag does not ellipsize its label", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
            layout.layoutInput.text.forEachIndexed { index, char ->
                if (!char.isWhitespace()) {
                    val glyph = layout.getBoundingBox(index)
                    assertTrue("$tag visible glyph $index stays inside its text bounds: $glyph / ${layout.size}",
                        glyph.left >= -1f && glyph.right <= layout.size.width + 1f)
                }
            }
        }
    }

    @Test fun searchHistoryAndSettingsKeepNativeStateInBothThemes() {
        val config = ui.activity.resources.configuration
        assertTrue("Use the physical narrow profile", config.screenWidthDp in 300..340)
        assertTrue("Large text is enabled before launch", config.fontScale in 1.25f..1.35f)
        lateinit var app: AppViewModel
        ui.runOnUiThread { app = ViewModelProvider(ui.activity)[AppViewModel::class.java] }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val categories = instrumentation.context.assets.open("personal-tag-summary.json").bufferedReader().use { PostParser.tagSummary(it.readText()) }
        val posts = instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use { PostParser.posts(it.readText(), categories) }
        assertEquals(2, posts.size)
        val before = app.settings.value
        val recordedBefore = app.recordHistory.value
        val query = "animated order:score"
        val historyBefore = app.history.value
        val db = LibraryDatabase.get(instrumentation.targetContext)
        val yesterday = LocalDate.now().minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking {
            app.personal.clearHistory(); app.personal.recording(true)
            PersonalStore(db) { yesterday }.record(posts[0])
            PersonalStore(db).record(posts[1])
            app.preferences.remember(query)
            app.preferences.columns(6)
            app.preferences.number("speed", 1.25f)
        }
        ui.waitUntil(10000) { app.viewedPosts.value.size == 2 && query in app.history.value && app.settings.value.columns == 6 && app.settings.value.speed == 1.25f }
        try {
            for (theme in listOf("dark", "light")) {
                runBlocking { app.preferences.theme(theme) }
                ui.waitUntil(10000) { app.settings.value.themeMode == theme }
                val expected = app.settings.value
                menu(R.string.ui_posts)
                ui.onNodeWithTag("search-entry").performClick()
                hideIme()
                UiEvidence.capture("maintenance-search-$theme.png")
                readableTool("open-combined-search", R.string.ui_search_tools)
                ui.onNodeWithTag("search-history-toggle").performScrollTo().performClick().assertIsSelected()
                readableTool("search-history-toggle", R.string.ui_search_history)
                ui.onNodeWithTag("search-history").assertExists()
                UiEvidence.capture("maintenance-search-$theme.png")
                ui.onNodeWithTag("open-combined-search").performScrollTo().performClick()
                ui.onNodeWithTag("edit-query-tool").assertIsSelected()
                ui.onNodeWithTag("search-reference-tool").performScrollTo().performClick().assertIsSelected()
                readableTool("search-reference-tool", R.string.ui_search_reference_language)
                ui.onNodeWithTag("combined-history-toggle").performScrollTo().performClick().assertIsSelected()
                ui.onNode(hasText(query) and hasAnyAncestor(hasTestTag("combined-history"))).performScrollTo().performClick()
                ui.onNodeWithTag("query-expression").performScrollTo().assertTextContains(query)
                ui.onNodeWithTag("query-order-score").performScrollTo().assertIsSelected()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_cancel), substring = false).performClick()

                menu(R.string.ui_browsing_history)
                ui.onNodeWithText(ui.activity.getString(R.string.ui_today), substring = false).assertIsDisplayed()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_yesterday), substring = false).performScrollTo().assertIsDisplayed()
                ui.onNodeWithTag("browsing-history-list").performScrollToIndex(0)
                UiEvidence.capture("maintenance-history-$theme.png")
                ui.onNodeWithTag("record-browsing-history").performClick()
                ui.waitUntil(10000) { !app.recordHistory.value }
                assertEquals("Turning recording off keeps existing history", 2, app.viewedPosts.value.size)
                ui.onNodeWithTag("record-browsing-history").performClick()
                ui.waitUntil(10000) { app.recordHistory.value }

                menu(R.string.ui_settings)
                ui.onNodeWithText(ui.activity.getString(R.string.ui_appearance), substring = false).assertIsDisplayed()
                UiEvidence.capture("maintenance-settings-$theme.png")
                ui.onNodeWithTag("setting-columns-6").performScrollTo().assertIsSelected()
                ui.onNodeWithTag("import-favorites").performScrollTo().assertIsDisplayed()
                ui.onNodeWithTag("export-favorites").performScrollTo().assertIsDisplayed()
                UiEvidence.capture("maintenance-settings-backup-$theme.png")
                assertEquals("Visiting grouped settings preserves every preference", expected, app.settings.value)
                ui.activityRule.scenario.recreate()
                ui.waitUntil(10000) { app.settings.value.themeMode == theme }
                assertEquals("Existing keys survive activity recreation", expected, app.settings.value)
            }
        } finally {
            runBlocking {
                app.personal.clearHistory(); app.personal.recording(recordedBefore)
                if (query !in historyBefore) app.preferences.deleteHistory(query)
                app.preferences.columns(before.columns); app.preferences.number("speed", before.speed)
                app.preferences.theme(before.themeMode)
                app.preferences.flag("lightTheme", before.lightTheme)
                app.preferences.theme(before.themeMode)
            }
        }
    }
}
