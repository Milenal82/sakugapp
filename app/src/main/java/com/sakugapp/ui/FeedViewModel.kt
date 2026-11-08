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
    private val _state = MutableStateFlow(FeedState(query = savedState["browseQuery"] ?: savedState["query"] ?: ""))
    val state = _state.asStateFlow()
    private val _selected = MutableStateFlow<Post?>(null)
    val selected = _selected.asStateFlow()
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()
    private var localSequence:List<Post>?=null
    private var detailRequest: Job? = null
    private var detailGeneration=0
    private var request: Job? = null
    private var generation = 0
    private val _searchMode=MutableStateFlow(false);val searchMode=_searchMode.asStateFlow()
    private var searchSnapshot:FeedState?=null
    fun resumeSearch(){val result=searchSnapshot ?: return;if(_searchMode.value)return;browseSnapshot=_state.value;_searchMode.value=true;request?.cancel();generation++;_state.value=result.copy(loading=false,refreshing=false);if(result.loading)loadMore()}
    private val results=LinkedHashMap<String,FeedState>(6,.75f,true)
    private val _resultSession=MutableStateFlow<String?>(null)
    val resultSession=_resultSession.asStateFlow()
    private fun saveResult(){_resultSession.value?.let{results[it]=_state.value;while(results.size>6)results.remove(results.keys.first())}}
    private var browseSnapshot:FeedState?=null
    val browsingState:FeedState get()=browseSnapshot ?: _state.value
    init { loadMore() }

    fun beginSearch(query:String):String {
        saveResult()
        if(browseSnapshot==null){browseSnapshot=_state.value;savedState["browseQuery"]=_state.value.query}
        _searchMode.value=true
        val token=java.util.UUID.randomUUID().toString();_resultSession.value=token
        search(query)
        return token
    }
    fun beginSampleSearch(query:String,posts:List<Post>):String {
        saveResult();request?.cancel();generation++
        if(browseSnapshot==null){browseSnapshot=_state.value;savedState["browseQuery"]=_state.value.query}
        _searchMode.value=true;close()
        val token=java.util.UUID.randomUUID().toString();_resultSession.value=token
        savedState["query"]=query
        _state.value=FeedState(query=query,posts=posts.distinctBy{it.id}.take(FeedState.MAX_RETAINED_POSTS),endReached=true,sampleOnly=true)
        return token
    }
    /** Entry stores only a token/query; evicted/process-lost samples are safely reloaded. */
    fun activateSearch(token:String,query:String,sampleOnly:Boolean=false){
        if(_resultSession.value==token && _searchMode.value)return
        saveResult();request?.cancel();generation++
        if(browseSnapshot==null){browseSnapshot=_state.value;savedState["browseQuery"]=_state.value.query}
        _searchMode.value=true;_resultSession.value=token
        val cached=results[token]
        _state.value=cached?.copy(loading=false,refreshing=false) ?: FeedState(query=query,sampleOnly=sampleOnly,endReached=sampleOnly)
        savedState["query"]=query
        if(!sampleOnly){if(cached?.refreshing==true)refresh()else if(cached==null || cached.loading)loadMore()}
    }
    /** Called only after navigation commits back to Posts, not during a cancelled back gesture. */
    fun restoreBrowse(){
        val original=browseSnapshot ?: return
        saveResult();_resultSession.value=null
        searchSnapshot=_state.value;_searchMode.value=false
        browseSnapshot=null;request?.cancel();generation++;close()
        savedState["query"]=original.query
        _state.value=original.copy(loading=false,refreshing=false)
        if(original.refreshing)refresh()else if(original.loading)loadMore()
    }
    fun search(query: String) {
        request?.cancel()
        close()
        generation++
        val normalized = query.trim()
        savedState["query"] = normalized
        _selected.value = null
        _state.value = FeedState(query = normalized)
        loadMore()
    }
    fun open(post: Post) {detailIsLocal=false;localSequence=null;select(post)}
    private fun select(post:Post){detailGeneration++;detailRequest?.cancel();_detailError.value=null;_selected.value=post}
    fun openLocal(post:Post,ordered:List<Post>){detailIsLocal=true;localSequence=ordered.distinctBy{it.id};select(post)}
    private data class DetailContext(val posts:List<Post>,val local:Boolean,val session:String?)
    private val detailContexts=LinkedHashMap<String,DetailContext>(8,.75f,true)
    fun captureDetail():String {
        val token=java.util.UUID.randomUUID().toString()
        detailContexts[token]=DetailContext(localSequence ?: _state.value.posts,detailIsLocal,_resultSession.value)
        while(detailContexts.size>8)detailContexts.remove(detailContexts.keys.first())
        return token
    }
    fun restoreDetail(id:Long,token:String?){
        val context=token?.let{detailContexts[it]}
        val cached=context?.posts?.firstOrNull{it.id==id}
        if(cached!=null){select(cached);localSequence=if(!context.local && context.session==_resultSession.value)null else context.posts;detailIsLocal=context.local}
        else if(_selected.value?.id!=id)openId(id)
    }
    private var detailIsLocal=false
    fun usesLocalSnapshot()=detailIsLocal
    fun adjacent(delta:Int):Post?{require(delta in setOf(-1,1));val posts=localSequence ?: _state.value.posts;val current=posts.indexOfFirst{it.id==_selected.value?.id};return if(current>=0)posts.getOrNull(current+delta)else null}
    fun selectAdjacent(post:Post){select(post)}
    fun prefetchNeighbors(){
        if(localSequence!=null)return
        val current=_state.value;val index=current.posts.indexOfFirst{it.id==_selected.value?.id}
        if(index>=0 && index>=current.posts.size-3 && !current.autoLoadPaused)loadMore()
    }
    fun openId(id: Long) {
        require(id>0)
        localSequence=null;detailIsLocal=false
        detailRequest?.cancel();_detailError.value=null;_selected.value=null
        val version=++detailGeneration
        detailRequest=viewModelScope.launch {
            try { val post=repository.page("id:$id",1).posts.firstOrNull{it.id==id} ?: throw com.sakugapp.data.DataFailure("Post not found.");if(version==detailGeneration)_selected.value=post }
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(version==detailGeneration)_detailError.value=e.message ?: "Unable to load post."}
        }
    }
    fun updateDuration(id:Long,url:String,value:Long){
        if(value<=0)return
        fun Post.update()=if(this.id==id && fileUrl==url)copy(durationMs=value)else this
        _state.value=_state.value.copy(posts=_state.value.posts.map{it.update()})
        browseSnapshot=browseSnapshot?.let{it.copy(posts=it.posts.map{post->post.update()})}
        _selected.value=_selected.value?.update()
        localSequence=localSequence?.map{it.update()}
        searchSnapshot=searchSnapshot?.let{it.copy(posts=it.posts.map{post->post.update()})}
    }
    fun updateScore(id: Long, score: Int?) {
        detailContexts.replaceAll{_,context->context.copy(posts=context.posts.map{if(it.id==id)it.copy(score=score)else it})}
        results.replaceAll{_,state->state.copy(posts=state.posts.map{if(it.id==id)it.copy(score=score)else it})}
        searchSnapshot=searchSnapshot?.let{it.copy(posts=it.posts.map{post->if(post.id==id)post.copy(score=score)else post})}
        localSequence=localSequence?.map{if(it.id==id)it.copy(score=score)else it}
        _state.value=_state.value.copy(posts=_state.value.posts.map{if(it.id==id)it.copy(score=score)else it})
        browseSnapshot=browseSnapshot?.let{it.copy(posts=it.posts.map{post->if(post.id==id)post.copy(score=score)else post})}
        _selected.value=_selected.value?.let{if(it.id==id)it.copy(score=score)else it}
    }
    fun close() { detailGeneration++;localSequence=null; detailRequest?.cancel();_detailError.value=null;_selected.value = null }
    fun refresh() {
        if(_state.value.refreshing || _state.value.sampleOnly)return
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
