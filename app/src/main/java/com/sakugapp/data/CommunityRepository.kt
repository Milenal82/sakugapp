// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.io.IOException
import com.sakugapp.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CommunityRepository(
    private val base: HttpUrl = "https://www.sakugabooru.com/".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build(),
) {
    private val mutation = Mutex()
    var language: String = "en"
        set(value) { require(value in PreferenceRules.languages);field=value }
    private fun request(path: String,fields: Map<String,String>? = null,csrf: String? = null): Request {
        val builder=Request.Builder().url(base.resolve(path)!!.newBuilder().addQueryParameter("locale",PreferenceRules.siteLanguage(language)).build())
            .header("Accept",if(path.substringBefore('?').endsWith(".json"))"application/json" else "text/html")
            .header("User-Agent","Sakugapp/${BuildConfig.VERSION_NAME} (+https://github.com/Milenal82/sakugapp)")
            .header("Accept-Language",PreferenceRules.acceptLanguage(language))
        csrf?.let { builder.header("X-CSRF-Token",it).header("X-Requested-With","XMLHttpRequest").header("Referer",base.toString()) }
        fields?.let { builder.post(FormBody.Builder().apply { it.forEach { (key,value)->add(key,value) } }.build()) }
        return builder.build()
    }
    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call=client.newCall(request);continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) { if(!continuation.isCancelled)continuation.resumeWithException(DataFailure("Network request failed. Check the connection and retry.")) }
            override fun onResponse(call:Call,response:Response) {
                try {
                    val text=response.use {
                        if(!it.isSuccessful)throw DataFailure(when(it.code) { 401,403->"Session expired or this account lacks permission (HTTP ${it.code}).";421,429->"Rate limited. Please wait before retrying.";else->"Website request failed (HTTP ${it.code})." })
                        val body=it.body ?: throw DataFailure("Empty website response.")
                        if(body.source().request(4L*1024*1024+1))throw DataFailure("Website response is too large.")
                        body.string()
                    };continuation.resume(text)
                } catch(e:Exception) { if(!continuation.isCancelled)continuation.resumeWithException(if(e is DataFailure)e else DataFailure("Unable to read the website response.")) }
            }
        })
    }
    private suspend fun token(path: String) = CommunityParser.csrf(execute(request(path)))
    suspend fun identity():Account? {
        val id=CommunityParser.homeUserId(execute(request("user/home"))) ?: return null
        val list=com.google.gson.JsonParser.parseString(execute(request("user.json?id=$id"))).asJsonArray
        val o=list.firstOrNull{it.isJsonObject && it.asJsonObject["id"]?.asLong==id}?.asJsonObject ?: throw DataFailure("Unable to verify the account profile.")
        val name=o["name"]?.asString?.takeIf{it.isNotBlank()} ?: throw DataFailure("Missing account name.")
        return Account(id,name)
    }
    suspend fun login(name: String,password: String): Account = mutation.withLock {
        require(name.isNotBlank() && password.isNotEmpty())
        val csrf=token("user/login")
        CommunityParser.login(execute(request("user/check.json",mapOf("username" to name,"password" to password),csrf)))
    }
    suspend fun currentVote(id:Long): Int = mutation.withLock {
        require(id>0)
        val csrf=token("post/show/$id")
        CommunityParser.currentVote(execute(request("post/vote.json",mapOf("id" to "$id"),csrf)))
    }
    suspend fun vote(id:Long,score:Int): VoteResult = mutation.withLock {
        require(id>0 && score in 0..3)
        val csrf=token("post/show/$id")
        CommunityParser.vote(execute(request("post/vote.json",mapOf("id" to "$id","score" to "$score"),csrf)),id)
    }
    suspend fun comments(id:Long,page:Int): List<Comment> {
        require(id>0 && page>0)
        return CommunityParser.comments(execute(request("comment.json?post_id=$id&page=$page"))).also { list->
            if(list.any { it.postId!=id })throw DataFailure("The server returned comments for a different post.")
        }
    }
    suspend fun comment(id:Long,body:String,editId:Long? = null): Unit = mutation.withLock {
        require(id>0 && body.isNotBlank() && body.length<=20000)
        val csrf=token("post/show/$id")
        val fields=mutableMapOf("comment[body]" to body)
        if(editId==null){fields["comment[post_id]"]="$id";fields["commit"]="Post"}else{require(editId>0);fields["id"]="$editId"}
        CommunityParser.success(execute(request(if(editId==null)"comment/create.json" else "comment/update.json",fields,csrf)))
    }
    suspend fun deleteComment(postId:Long,id:Long): Unit = mutation.withLock {
        require(postId>0 && id>0)
        val csrf=token("post/show/$postId")
        CommunityParser.success(execute(request("comment/destroy.json",mapOf("id" to "$id"),csrf)))
    }
    suspend fun suggestions(prefix: String): List<String> {
        if(prefix.length<2)return emptyList()
        val path="tag.json?"+HttpUrl.Builder().scheme("https").host("example.org").addQueryParameter("name",prefix).addQueryParameter("limit","8").build().encodedQuery
        val array=com.google.gson.JsonParser.parseString(execute(request(path))).asJsonArray
        return array.mapNotNull { it.asJsonObject.get("name")?.asString }.distinct().take(8)
    }
}
