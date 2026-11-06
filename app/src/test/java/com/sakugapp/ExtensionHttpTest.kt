// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp
import com.sakugapp.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
class ExtensionHttpTest {
 @Test fun emptyDateResultsAndEncodedQueriesRemainRealEmptyResults()=runBlocking{
  val server=MockWebServer();server.start()
  try{server.enqueue(MockResponse().setBody("[]"));val query="naruto ~fighting ~liquid date:2024-02-29...2024-03-01 score:>=3 order:score"
   val page=SakugaRepository(baseUrl=server.url("/")).page(query,1);assertTrue(page.posts.isEmpty());assertEquals(0,page.rawCount)
   val request=server.takeRequest();assertEquals(query,request.requestUrl!!.queryParameter("tags"));assertEquals("1",request.requestUrl!!.queryParameter("page"))
  }finally{server.shutdown()}
 }
 @Test fun directoriesUseVerifiedTypeOrderAndPageAndSurfaceRateLimits()=runBlocking{
  val server=MockWebServer();server.start()
  try{val repository=SakugaRepository(baseUrl=server.url("/"));server.enqueue(MockResponse().setBody("[]"))
   assertTrue(repository.directory(TagCategory.COPYRIGHT,"count",2).isEmpty())
   val u=server.takeRequest().requestUrl!!;assertEquals("3",u.queryParameter("type"));assertEquals("count",u.queryParameter("order"));assertEquals("2",u.queryParameter("page"));assertEquals("30",u.queryParameter("limit"))
   server.enqueue(MockResponse().setResponseCode(429));try{repository.directory(TagCategory.ARTIST,"date",1);fail()}catch(e:DataFailure){assertTrue(e.message!!.contains("429"))}
  }finally{server.shutdown()}
 }
}
