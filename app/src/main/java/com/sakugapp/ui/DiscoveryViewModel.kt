// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DiscoveryState(val sample:Sample?=null,val statistics:ArtistStatistics?=null,val groups:Map<EpisodeKey,List<Post>> = emptyMap(),val loading:Boolean=false,val error:String?=null)
class DiscoveryViewModel(private val repository:DiscoveryRepository):ViewModel(){
    private val _state=MutableStateFlow(DiscoveryState());val state=_state.asStateFlow()
    private var work:Job?=null;private var generation=0
    fun select(tag:String,type:TagCategory,refresh:Boolean=false,pages:Int=if(type==TagCategory.ARTIST)1 else 3){
        if(!refresh && state.value.sample?.let{it.tag==tag && it.type==type}==true)return
        work?.cancel();val version=++generation
        val warm=if(refresh)null else repository.cached(tag,type)
        _state.value=DiscoveryState(sample=warm,loading=true)
        work=viewModelScope.launch{try{
            suspend fun accept(sample:Sample){val calculated=withContext(Dispatchers.Default){ArtistStatsCalculator.calculate(tag,sample.posts) to SourceEpisodeParser.groups(sample.posts)};if(version==generation)_state.value=DiscoveryState(sample,calculated.first,calculated.second,true)}
            if(warm!=null)accept(warm)
            val value=if(warm!=null && !refresh)warm else repository.load(tag,type,pages,refresh){sample->if(version==generation)_state.value=DiscoveryState(sample,ArtistStatsCalculator.calculate(tag,sample.posts),SourceEpisodeParser.groups(sample.posts),true)}
            accept(value)
        }catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)_state.value=_state.value.copy(error=e.message ?: "Unable to load data.")}finally{if(version==generation)_state.value=_state.value.copy(loading=false)}}
    }
    fun more(){val current=state.value.sample ?: return;if(state.value.loading || current.ended || current.capped)return
        work?.cancel();val version=++generation;_state.value=state.value.copy(loading=true,error=null)
        work=viewModelScope.launch{try{val sample=repository.load(current.tag,current.type,if(current.type==TagCategory.ARTIST)1 else 3){if(version==generation)_state.value=DiscoveryState(it,ArtistStatsCalculator.calculate(it.tag,it.posts),SourceEpisodeParser.groups(it.posts),true)}
            val result=withContext(Dispatchers.Default){ArtistStatsCalculator.calculate(sample.tag,sample.posts) to SourceEpisodeParser.groups(sample.posts)}
            if(version==generation)_state.value=DiscoveryState(sample,result.first,result.second,true)
        }catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)_state.value=_state.value.copy(error=e.message)}finally{if(version==generation)_state.value=_state.value.copy(loading=false)}}
    }
    fun retry(tag:String,type:TagCategory){if(state.value.sample==null)select(tag,type)else more()}
    fun cancel(){work?.cancel();generation++;_state.value=_state.value.copy(loading=false)}
}
