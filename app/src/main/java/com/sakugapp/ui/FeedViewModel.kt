// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakugapp.data.FeedState
import com.sakugapp.data.Post
import com.sakugapp.data.PostRepository
import com.sakugapp.data.SakugaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val repository: PostRepository = SakugaRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(FeedState(query = savedState["query"] ?: ""))
    val state = _state.asStateFlow()
    private val _selected = MutableStateFlow<Post?>(null)
    val selected = _selected.asStateFlow()
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()
    private var detailRequest: Job? = null
    private var request: Job? = null
    private var generation = 0
    init { loadMore() }

    fun search(query: String) {
        request?.cancel()
        generation++
        val normalized = query.trim()
        savedState["query"] = normalized
        _selected.value = null
        _state.value = FeedState(query = normalized)
        loadMore()
    }
    fun open(post: Post) { detailRequest?.cancel(); _detailError.value=null; _selected.value = post }
    fun openId(id: Long) {
        require(id>0)
        detailRequest?.cancel();_detailError.value=null;_selected.value=null
        detailRequest=viewModelScope.launch {
            try { _selected.value=repository.page("id:$id",1).posts.firstOrNull{it.id==id} ?: throw com.sakugapp.data.DataFailure("Post not found.") }
            catch(e:CancellationException){throw e}
            catch(e:Exception){_detailError.value=e.message ?: "Unable to load post."}
        }
    }
    fun updateScore(id: Long, score: Int?) {
        _state.value=_state.value.copy(posts=_state.value.posts.map{if(it.id==id)it.copy(score=score)else it})
        _selected.value=_selected.value?.let{if(it.id==id)it.copy(score=score)else it}
    }
    fun close() { detailRequest?.cancel();_detailError.value=null;_selected.value = null }
    fun loadMore() {
        if (_state.value.loading || _state.value.endReached) return
        val current = _state.value
        val version = generation
        _state.value = current.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = repository.page(current.query, current.nextPage)
                if (version == generation) _state.value = _state.value.append(page, SakugaRepository.PAGE_SIZE)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == generation) _state.value = _state.value.copy(
                    loading = false, error = e.message ?: "Loading failed. Please retry.")
            }
        }
    }
}
