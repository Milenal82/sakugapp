// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

class QuizPoolSamplingTest {
 private val names=listOf("work_a","work_b","work_c")
 private val types=listOf("a","b","c","d").associateWith{TagCategory.ARTIST}+names.associateWith{TagCategory.COPYRIGHT}
 private fun post(id:Long,works:List<String>)=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first().copy(id=id,fileExt="mp4",fileUrl="https://www.sakugabooru.com/data/test.mp4",tags=listOf(Tag("a",TagCategory.ARTIST))+works.map{Tag(it,TagCategory.COPYRIGHT)})
 @Test fun eachShuffledCycleVisitsEveryWorkExactlyOnce(){
  val rotation=QuizWorkRotation(names,Random(7))
  repeat(10){assertEquals(names.toSet(),List(3){rotation.next()}.toSet())}
 }
 @Test fun cachedPostsCannotMonopolizeQuestionsAndQueriesNeverAndWorks()=runBlocking{
  val queries=mutableListOf<String>()
  val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{queries+=query;val name=query.substringBefore(' ');return PostPage((1..10).map{post((names.indexOf(name)+1)*100L+it,listOf(name))},10)}},{types},Random(9))
  repo.start(QuizSettings(count=10,custom=true,works=names));val used=mutableSetOf<Long>();val seen=mutableListOf<String>()
  repeat(6){val q=repo.next(used);assertTrue(used.add(q.post.id));seen+=q.post.tags.single{it.category==TagCategory.COPYRIGHT}.name}
  assertEquals(names.associateWith{2},seen.groupingBy{it}.eachCount());assertEquals(3,queries.size)
  assertTrue(queries.all{q->names.count{it in q.split(' ')}==1})
 }
 @Test fun oneEmptyWorkDoesNotPreventOthersAndForeignPostsNeverLeak()=runBlocking{
  var emptyCalls=0
  val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{val work=query.substringBefore(' ');if(work=="work_a"){emptyCalls++;return PostPage(listOf(post(999,listOf("outside"))),1)};return PostPage((1L..8L).map{post(it,listOf(work))},8)}},{types},Random(4))
  repo.start(QuizSettings(custom=true,works=names.take(2)));val used=mutableSetOf<Long>()
  repeat(4){val q=repo.next(used);assertTrue(q.post.tags.any{it.name=="work_b"});assertTrue(used.add(q.post.id));assertNotEquals(999L,q.post.id)}
  assertTrue(emptyCalls in 1..2)
 }
 @Test fun sharedPostsAcrossWorksStillAppearOnlyOnce()=runBlocking{
  val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{val name=query.substringBefore(' ');return PostPage(listOf(post(7,names.take(2)),post(if(name=="work_a")8 else 9,listOf(name))),2)}},{types},Random(1))
  repo.start(QuizSettings(custom=true,works=names.take(2)));val used=mutableSetOf<Long>()
  repeat(3){assertTrue(used.add(repo.next(used).post.id))};assertEquals(setOf(7L,8L,9L),used)
 }
 @Test fun allThirtyWorksHaveBoundedOpportunitiesWithoutInfiniteFetching()=runBlocking{
  val works=(1..30).map{"work_$it"};val queries=mutableListOf<String>()
  val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{queries+=query.substringBefore(' ');return PostPage(emptyList(),0)}},{types+works.associateWith{TagCategory.COPYRIGHT}},Random(2))
  repo.start(QuizSettings(custom=true,works=works));try{repo.next(emptySet());fail()}catch(e:DataFailure){assertEquals("quiz_insufficient",e.message)}
  assertEquals(60,queries.size);assertEquals(works.associateWith{2},queries.groupingBy{it}.eachCount())
 }
 @Test fun networkFailureIsNotAnEmptyPoolAndCanBeRetried()=runBlocking{
  var calls=0;val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{calls++;if(calls==1)throw DataFailure("HTTP 429");return PostPage(listOf(post(1,listOf("work_a"))),1)}},{types})
  repo.start(QuizSettings(custom=true,works=listOf("work_a")));try{repo.next(emptySet());fail()}catch(e:DataFailure){assertEquals("HTTP 429",e.message)}
  assertEquals(1L,repo.next(emptySet()).post.id);assertEquals(2,calls)
 }
 @Test fun poolLimitPreservesOrderAndDuplicatesDoNotConsumeCapacity(){
  val old=(1..20).map{"work_$it"};var pool=old
  for(i in 21..30)pool=QuizPoolRules.add(pool,"work_$i")
  assertEquals(old,pool.take(20));assertEquals(pool,QuizPoolRules.add(pool,"work_1"));assertEquals(30,pool.size)
  assertThrows(DataFailure::class.java){QuizPoolRules.add(pool,"work_31")};assertEquals(30,pool.size)
 }
}
