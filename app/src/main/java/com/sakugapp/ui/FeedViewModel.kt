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
    private var localSequence:List<Post>?=null
    private var detailRequest: Job? = null
    private var request: Job? = null
    private var generation = 0
    init { loadMore() }

    fun search(query: String) {
        request?.cancel()
        localSequence=null
        generation++
        val normalized = query.trim()
        savedState["query"] = normalized
        _selected.value = null
        _state.value = FeedState(query = normalized)
        loadMore()
    }
    fun open(post: Post) {localSequence=null;select(post)}
    private fun select(post:Post){detailRequest?.cancel();_detailError.value=null;_selected.value=post}
    fun openLocal(post:Post,ordered:List<Post>){localSequence=ordered.distinctBy{it.id};select(post)}
    fun usesLocalSnapshot()=localSequence!=null
    fun adjacent(delta:Int):Post?{require(delta in setOf(-1,1));val posts=localSequence ?: _state.value.posts;val current=posts.indexOfFirst{it.id==_selected.value?.id};return if(current>=0)posts.getOrNull(current+delta)else null}
    fun selectAdjacent(post:Post){select(post)}
    fun prefetchNeighbors(){
        if(localSequence!=null)return
        val current=_state.value;val index=current.posts.indexOfFirst{it.id==_selected.value?.id}
        if(index>=0 && index>=current.posts.size-3 && !current.autoLoadPaused)loadMore()
    }
    fun openId(id: Long) {
        require(id>0)
        localSequence=null
        detailRequest?.cancel();_detailError.value=null;_selected.value=null
        detailRequest=viewModelScope.launch {
            try { _selected.value=repository.page("id:$id",1).posts.firstOrNull{it.id==id} ?: throw com.sakugapp.data.DataFailure("Post not found.") }
            catch(e:CancellationException){throw e}
            catch(e:Exception){_detailError.value=e.message ?: "Unable to load post."}
        }
    }
    fun updateDuration(id:Long,url:String,value:Long){
        if(value<=0)return
        fun Post.update()=if(this.id==id && fileUrl==url)copy(durationMs=value)else this
        _state.value=_state.value.copy(posts=_state.value.posts.map{it.update()})
        _selected.value=_selected.value?.update()
        localSequence=localSequence?.map{it.update()}
    }
    fun updateScore(id: Long, score: Int?) {
        _state.value=_state.value.copy(posts=_state.value.posts.map{if(it.id==id)it.copy(score=score)else it})
        _selected.value=_selected.value?.let{if(it.id==id)it.copy(score=score)else it}
    }
    fun close() { localSequence=null; detailRequest?.cancel();_detailError.value=null;_selected.value = null }
    fun refresh() {
        if(_state.value.refreshing)return
        request?.cancel();generation++
        val current=_state.value;val version=generation
        _state.value=current.copy(loading=false,refreshing=true,refreshError=null)
        request=viewModelScope.launch{
            try{
                val first=repository.page(current.query,1)
                if(version==generation)_state.value=FeedState(query=current.query,refreshRevision=current.refreshRevision+1).append(first,repository.pageSize)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(version==generation)_state.value=_state.value.copy(refreshing=false,refreshError=e.message ?: "Loading failed. Please retry.")}
        }
    }
    fun loadMore() {
        if (_state.value.loading || _state.value.refreshing || _state.value.endReached) return
        val current = _state.value
        val version = generation
        _state.value = current.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = repository.page(current.query, current.nextPage)
                if (version == generation) _state.value = _state.value.append(page, repository.pageSize)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == generation) _state.value = _state.value.copy(
                    loading = false, error = e.message ?: "Loading failed. Please retry.")
            }
        }
    }
}
