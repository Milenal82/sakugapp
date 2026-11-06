// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class QuizState(val settings:QuizSettings=QuizSettings(),val question:QuizQuestion?=null,val answers:List<QuizAnswer> = emptyList(),val used:Set<Long> = emptySet(),val selected:String?=null,val loading:Boolean=false,val started:Boolean=false,val result:Boolean=false,val error:String?=null)
class QuizViewModel(private val repository:QuizRepository):ViewModel(){
    private val _state=MutableStateFlow(QuizState());val state=_state.asStateFlow();private var job:Job?=null;private var generation=0
    fun start(settings:QuizSettings){job?.cancel();val g=++generation;_state.value=QuizState(settings=settings,loading=true,started=true)
        job=viewModelScope.launch{try{repository.start(settings);val q=repository.next(emptySet());if(g==generation)_state.value=state.value.copy(question=q,used=setOf(q.post.id),loading=false)}catch(e:CancellationException){throw e}catch(e:Exception){if(g==generation)_state.value=state.value.copy(loading=false,error=e.message)}}
    }
    fun choose(value:String){val s=state.value;val q=s.question ?: return;if(s.loading || s.selected!=null)return;val answers=QuizGenerator.answer(q,value,s.answers);if(answers.size>s.answers.size)_state.value=s.copy(answers=answers,selected=value)}
    fun next(){val s=state.value;if(s.loading)return;if(s.answers.size>=s.settings.count){_state.value=s.copy(result=true);return}
        val g=++generation;_state.value=s.copy(loading=true,selected=null,error=null,question=null)
        job=viewModelScope.launch{try{val q=repository.next(s.used);if(g==generation)_state.value=state.value.copy(question=q,used=s.used+q.post.id,loading=false)}catch(e:CancellationException){throw e}catch(e:Exception){if(g==generation)_state.value=state.value.copy(loading=false,error=e.message)}}
    }
    fun results(){job?.cancel();generation++;_state.value=state.value.copy(result=true,loading=false)}
    fun home(){job?.cancel();generation++;_state.value=QuizState()}
    fun resume(){val s=state.value;if(s.started && !s.result && s.question==null && !s.loading && s.error==null){if(s.used.isEmpty())start(s.settings)else next()}}
    fun stopLoading(){job?.cancel();generation++;_state.value=state.value.copy(loading=false)}
}
