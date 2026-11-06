// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WebsiteFavoritesViewModel(private val source:WebsiteFavoritesSource,private val account:StateFlow<Account?>,revision:StateFlow<Int>):ViewModel(){
    private val _state=MutableStateFlow(FeedState());val state=_state.asStateFlow()
    private val _grade=MutableStateFlow(0);val grade=_grade.asStateFlow()
    private var active=false;private var request:Job?=null;private var generation=0
    init{viewModelScope.launch{combine(account,revision){a,r->a to r}.collect{reset();if(active && it.first!=null)load()}}}
    private fun reset(){request?.cancel();generation++;_state.value=FeedState()}
    fun active(value:Boolean){active=value;if(!value){request?.cancel();generation++;_state.value=state.value.copy(loading=false,refreshing=false)}else if(account.value!=null && state.value.posts.isEmpty() && !state.value.loading && state.value.error==null)load()}
    fun grade(value:Int){require(value in 0..3);if(_grade.value!=value){_grade.value=value;reset();load()}}
    fun load(refresh:Boolean=false){
        val who=account.value ?: return
        if(state.value.loading || state.value.refreshing || (!refresh && state.value.endReached))return
        request?.cancel();val version=++generation;val rank=grade.value;val prior=state.value
        _state.value=prior.copy(loading=!refresh,refreshing=refresh,error=null,refreshError=null)
        request=viewModelScope.launch{try{val result=source.page(who,rank,if(refresh)1 else prior.nextPage)
            if(version==generation && account.value==who)_state.value=(if(refresh)FeedState()else prior).append(result,20)
        }catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)_state.value=prior.copy(loading=false,refreshing=false,error=e.message)}}
    }
}
