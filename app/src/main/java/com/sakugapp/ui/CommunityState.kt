// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.runtime.*
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

/** Independent post-scoped state; collapsing comments never cancels or reloads their thread. */
class CommentThread(private val id:Long,private val repository:CommunityRepository,private val scope:CoroutineScope){
    var comments by mutableStateOf<List<Comment>>(emptyList());private set
    var page by mutableIntStateOf(1);private set
    var end by mutableStateOf(false);private set
    var loaded by mutableStateOf(false);private set
    var loading by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var draft by mutableStateOf("");private set
    var editing by mutableStateOf<Long?>(null);private set
    var sending by mutableStateOf(false);private set
    var notice by mutableStateOf<Int?>(null);private set
    private var retryReset=true
    fun draft(value:String){draft=value.take(20000)}
    fun edit(comment:Comment){editing=comment.id;draft=comment.body}
    fun cancelEdit(){editing=null;draft=""}
    fun expand(){if(!loaded && !loading && error==null)load(true)}
    fun retry(){load(retryReset)}
    fun more(){load(false)}
    private suspend fun fetch(reset:Boolean){
        if(loading)return
        loading=true;error=null;retryReset=reset
        try{val requested=if(reset)1 else page;val result=repository.comments(id,requested)
            comments=((if(reset)emptyList()else comments)+result).distinctBy{it.id};page=requested+1;end=result.size<25;loaded=true
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false}
    }
    private fun load(reset:Boolean){if(loading)return;scope.launch(Dispatchers.Main.immediate){fetch(reset)}}
    fun submit(){
        if(sending || loading || draft.isBlank())return
        val body=draft;val edit=editing;sending=true;error=null
        scope.launch(Dispatchers.Main.immediate){try{repository.comment(id,body,edit);draft="";editing=null;notice=R.string.ui_comment_accepted_by_the_server;fetch(true)}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{sending=false}}
    }
    fun delete(comment:Comment){
        if(sending || loading)return;sending=true;error=null
        scope.launch(Dispatchers.Main.immediate){try{repository.deleteComment(id,comment.id);notice=R.string.ui_deletion_confirmed_by_the_server;fetch(true)}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{sending=false}}
    }
}
class PostVote(private val id:Long,private val repository:CommunityRepository,private val scope:CoroutineScope){
    var value by mutableStateOf<Int?>(null);private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var confirmed by mutableStateOf(false);private set
    private var identity:Long?=null;private var generation=0
    fun identify(account:Account?){if(identity==account?.id)return;identity=account?.id;generation++;value=null;error=null;confirmed=false;if(account!=null)refresh()}
    fun clearError(){error=null}
    fun refresh(){val who=identity ?: return;val version=generation
        scope.launch(Dispatchers.Main.immediate){try{val result=repository.currentVote(id);if(who==identity && version==generation){value=result;error=null}}
            catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)error=e.message}}
    }
    fun choose(score:Int,onScore:(Int?)->Unit){
        require(score in 0..3);if(busy || identity==null)return
        val version=generation;busy=true;error=null;confirmed=false
        scope.launch(Dispatchers.Main.immediate){try{val result=repository.vote(id,score);onScore(result.score)
            if(version==generation){value=score;confirmed=true;refresh()}
        }catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)error=e.message}finally{busy=false}}
    }
}
