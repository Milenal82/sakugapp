// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import kotlinx.coroutines.*
import kotlin.random.Random

data class QuizSettings(val count:Int=20,val custom:Boolean=false,val works:List<String> = emptyList()){
    companion object{val counts=listOf(5,10,20,30,40,50)}
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
class QuizRepository(private val posts:PostRepository=SakugaRepository(pageSize=100),private val catalog:suspend()->Map<String,TagCategory> = {(posts as SakugaRepository).classifications()},private val random:Random=Random.Default){
    private var candidates=emptyList<String>();private val buffer=ArrayDeque<Post>();private var attempts=0;private var settings=QuizSettings()
    suspend fun start(value:QuizSettings)=withContext(Dispatchers.Default){
        require(value.count in QuizSettings.counts && value.works.size<=20)
        val types=catalog()
        if(value.custom && (value.works.isEmpty() || value.works.any{types[it]!=TagCategory.COPYRIGHT}))throw DataFailure("quiz_pool")
        val artists=types.asSequence().filter{it.value==TagCategory.ARTIST && it.key!="artist_unknown"}.map{it.key}.take(4096).toList()
        if(artists.size<4)throw DataFailure("quiz_insufficient")
        candidates=artists;settings=value;buffer.clear();attempts=0
    }
    suspend fun next(used:Set<Long>):QuizQuestion=withContext(Dispatchers.Default){
        while(true){
            currentCoroutineContext().ensureActive()
            while(buffer.isNotEmpty()){QuizGenerator.question(buffer.removeFirst(),candidates,used,settings,random)?.let{return@withContext it}}
            if(attempts>=12)throw DataFailure("quiz_insufficient")
            val scope=if(settings.custom)settings.works.random(random)+" "else""
            attempts++
            val page=posts.page(scope+"-production_materials -presumed -artist_unknown order:random",1)
            currentCoroutineContext().ensureActive()
            buffer.addAll(page.posts.take(100))
        }
        @Suppress("UNREACHABLE_CODE") error("quiz_insufficient")
    }
}
