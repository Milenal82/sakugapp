// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class QuizSettings(val count:Int=20,val custom:Boolean=false,val works:List<String> = emptyList()){
    companion object{const val MAX_WORKS=30;val counts=listOf(5,10,20,30,40,50)}
}
data class QuizQuestion(val post:Post,val correct:String,val options:List<String>)
data class QuizAnswer(val postId:Long,val selected:String,val correct:String){val right get()=selected==correct}
object QuizGenerator {
    fun question(post:Post,artists:List<String>,used:Set<Long>,settings:QuizSettings,random:Random=Random.Default):QuizQuestion? {
        if(post.id<=0 || post.id in used || !post.isVideo || post.tags.any{it.category==TagCategory.UNKNOWN || it.name in setOf("production_materials","presumed","artist_unknown")})return null
        if(settings.custom && post.tags.none{it.category==TagCategory.COPYRIGHT && it.name in settings.works})return null
        val correct=post.tags.filter{it.category==TagCategory.ARTIST}.distinctBy{it.name}.singleOrNull()?.name ?: return null
        val wrong=artists.filter{it!=correct && LibraryCodec.tagName(it)}.distinct().shuffled(random).take(3)
        if(wrong.size!=3)return null
        return QuizQuestion(post,correct,(wrong+correct).shuffled(random))
    }
    fun answer(question:QuizQuestion,selected:String,previous:List<QuizAnswer>):List<QuizAnswer>{
        if(previous.any{it.postId==question.post.id} || selected !in question.options)return previous
        return previous+QuizAnswer(question.post.id,selected,question.correct)
    }
    fun accuracy(answers:List<QuizAnswer>)=if(answers.isEmpty())null else answers.count{it.right}*100.0/answers.size
}
/** Bounded metadata fetching only; no videos are preloaded in bulk. */
class QuizRepository(private val posts:PostRepository=SakugaRepository(pageSize=100),private val catalog:suspend()->Map<String,TagCategory> = {(posts as SakugaRepository).classifications()},private val random:Random=Random.Default,private val supplement:suspend(Int)->List<DirectoryTag> = {page->if(posts is SakugaRepository)posts.directory(TagCategory.ARTIST,"count",page)else emptyList()}){
    private val lock=Mutex()
    private var candidates=emptyList<String>();private val buffers=mutableMapOf<String,ArrayDeque<Post>>()
    private var attempts=0;private var settings=QuizSettings();private var rotation=QuizWorkRotation(listOf(""),random)
    private val emptyRounds=mutableMapOf<String,Int>();private var retryWork:String?=null
    suspend fun start(value:QuizSettings)=withContext(Dispatchers.Default){lock.withLock{
        require(value.count in QuizSettings.counts && value.works.size<=QuizSettings.MAX_WORKS)
        val types=catalog()
        if(value.custom && (value.works.isEmpty() || value.works.any{types[it]!=TagCategory.COPYRIGHT}))throw DataFailure("quiz_pool")
        val artists=types.asSequence().filter{it.value==TagCategory.ARTIST && it.key!="artist_unknown"}.map{it.key}.take(4096).toMutableList()
        for(page in 1..2){
            if(artists.distinct().size>=4)break
            currentCoroutineContext().ensureActive()
            artists+=supplement(page).filter{it.category==TagCategory.ARTIST && it.name!="artist_unknown" && LibraryCodec.tagName(it.name)}.map{it.name}
        }
        if(artists.distinct().size<4)throw DataFailure("quiz_insufficient")
        currentCoroutineContext().ensureActive()
        candidates=artists.distinct().take(4096);settings=value.copy(works=value.works.distinct());buffers.clear();emptyRounds.clear();retryWork=null;attempts=0;rotation=QuizWorkRotation(if(value.custom)settings.works else listOf(""),random)
    }}
    suspend fun next(used:Set<Long>):QuizQuestion=withContext(Dispatchers.Default){lock.withLock{
        val works=if(settings.custom)settings.works else listOf("")
        val budget=if(settings.custom)maxOf(12,works.size*2)else 12
        while(true){
            var requested=false
            repeat(works.size){
                currentCoroutineContext().ensureActive()
                val work=retryWork ?: rotation.next();retryWork=null
                val buffer=buffers.getOrPut(work){ArrayDeque()}
                fun take():QuizQuestion?{
                    while(buffer.isNotEmpty()){
                        val post=buffer.removeFirst()
                        if(work.isNotEmpty() && post.tags.none{it.name==work && it.category==TagCategory.COPYRIGHT})continue
                        QuizGenerator.question(post,candidates,used,settings,random)?.let{return it}
                    }
                    return null
                }
                take()?.let{return@withLock it}
                if(attempts<budget && (emptyRounds[work] ?: 0)<2){
                    attempts++;requested=true
                    val prefix=if(work.isEmpty())""else"$work "
                    val page=try{posts.page(prefix+"-production_materials -presumed -artist_unknown order:random",1)}catch(e:Exception){retryWork=work;throw e}
                    currentCoroutineContext().ensureActive()
                    buffer.addAll(page.posts.take(100))
                    take()?.let{emptyRounds[work]=0;return@withLock it}
                    emptyRounds[work]=(emptyRounds[work] ?: 0)+1
                }
            }
            if(!requested)throw DataFailure("quiz_insufficient")
        }
        @Suppress("UNREACHABLE_CODE") error("quiz_insufficient")
    }}
}

/** Every cycle visits each selected work once; cached posts never monopolize a round. */
class QuizWorkRotation(works:List<String>,private val random:Random=Random.Default){
    private val works=works.distinct().also{require(it.isNotEmpty())};private val queue=ArrayDeque<String>()
    fun next():String{if(queue.isEmpty())queue.addAll(works.shuffled(random));return queue.removeFirst()}
}
object QuizPoolRules {
    fun add(previous:List<String>,value:String):List<String>{
        require(LibraryCodec.tagName(value))
        val current=previous.filter{LibraryCodec.tagName(it)}.distinct()
        if(value in current)return current
        if(current.size>=QuizSettings.MAX_WORKS)throw DataFailure("quiz_pool_limit")
        return current+value
    }
}
