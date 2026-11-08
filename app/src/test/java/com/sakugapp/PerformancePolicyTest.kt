// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PerformancePolicyTest {
    private fun viewport(ids:List<Long> = listOf(1),scrolling:Boolean=false,enabled:Boolean=true,active:Boolean=true,foreground:Boolean=true,revision:Int=0)=PreviewViewport(ids,scrolling,active,foreground,enabled,revision)
    private fun post(id:Long)=PostParser.posts("""[{"id":$id,"file_url":"https://www.sakugabooru.com/data/$id.mp4","file_ext":"mp4"}]""").single()
    @Test fun disablingAutoReleasesAutomaticSelectionButKeepsManualSelection(){
        assertNull(PreviewSelection(1,true).retain(viewport(enabled=false)).id)
        assertEquals(1L,PreviewSelection(1,false).retain(viewport(enabled=false)).id)
        assertNull(PreviewSelection(1,false).retain(viewport(active=false)).id)
        assertNull(PreviewSelection(1,true).retain(viewport(ids=listOf(2))).id)
    }
    @Test fun anOnscreenAutomaticPlayerSurvivesScrollWhileTheUiPausesIt(){
        val selection=PreviewSelection(1,true)
        assertEquals(selection,selection.retain(viewport(scrolling=true)))
        assertFalse(PreviewSelection().canStart(viewport(scrolling=true)))
        assertFalse(PreviewSelection().canStart(viewport(foreground=false)))
    }
    @Test fun manualActionsAreImmediateAndStopIsNotUndoneByAnIdleLayout(){
        val playing=PreviewSelection().manual(1);assertEquals(1L,playing.id);assertFalse(playing.automatic)
        val stopped=playing.manual(1);assertNull(stopped.id);assertTrue(stopped.pausedByUser)
        assertFalse(stopped.retain(viewport()).canStart(viewport()))
        val moved=stopped.retain(viewport(scrolling=true));assertTrue(moved.canStart(viewport()))
    }
    @Test fun automaticCandidatesWaitAndScrollingCancelsTheOldCandidate()=runTest{
        var selection=PreviewSelection();val changes=MutableStateFlow(viewport());val selected=mutableListOf<Long>()
        val work=launch{changes.collectLatest{view->selection=selection.retain(view);settledPreview(view,{selection},{listOf(post(1),post(2))})?.let{selection=it;selected+=it.id!!}}}
        runCurrent();advanceTimeBy(AUTO_PREVIEW_SETTLE_MS-1);assertTrue(selected.isEmpty())
        changes.value=viewport(ids=listOf(2),scrolling=true);runCurrent();advanceTimeBy(AUTO_PREVIEW_SETTLE_MS+1);assertTrue(selected.isEmpty())
        changes.value=viewport(ids=listOf(2));runCurrent();advanceTimeBy(AUTO_PREVIEW_SETTLE_MS);runCurrent()
        assertEquals(listOf(2L),selected);assertTrue(selection.automatic);work.cancel()
    }
    @Test fun manualChoiceAndBackgroundCancelPendingAutomaticStart()=runTest{
        var selection=PreviewSelection()
        val pending=async{settledPreview(viewport(),{selection},{listOf(post(1),post(2))})}
        runCurrent();selection=selection.manual(2);advanceUntilIdle();assertNull(pending.await());assertEquals(2L,selection.id)
        assertNull(settledPreview(viewport(foreground=false),{PreviewSelection()},{listOf(post(1))}))
    }
    @Test fun unrelatedOrRepeatedDurationFactsKeepTheExactFeedAndPostObjects(){
        val original=FeedState(posts=listOf(post(1),post(2)))
        assertSame(original,presentFeed(original,mapOf("https://www.sakugabooru.com/data/other.mp4" to 100)))
        val updated=presentFeed(original,mapOf(original.posts.first().fileUrl!! to 1234L))
        assertEquals(1234L,updated.posts.first().durationMs);assertSame(original.posts[1],updated.posts[1]);assertNull(original.posts.first().durationMs)
        assertSame(updated,presentFeed(updated,mapOf(updated.posts.first().fileUrl!! to 1234L)))
        assertSame(original,presentFeed(original,mapOf(original.posts.first().fileUrl!! to -1L)))
    }
}
