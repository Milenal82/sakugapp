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
    fun open(post: Post) { _selected.value = post }
    fun close() { _selected.value = null }
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
                    loading = false, error = e.message ?: "加载失败，请重试。")
            }
        }
    }
}
