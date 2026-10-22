// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.*
import org.jsoup.Jsoup

data class Account(val id: Long, val name: String)
data class Comment(val id: Long,val postId: Long,val author: String?,val authorId: Long?,val body: String,val createdAt: String?)
data class VoteResult(val postId: Long,val score: Int?)
object CommunityParser {
    fun obj(body: String): JsonObject = try { JsonParser.parseString(body).asJsonObject }
        catch(e: Exception) { throw DataFailure("The server returned an invalid response.") }
    private fun JsonObject.string(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject.long(k: String): Long? = get(k)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
    fun login(body: String): Account {
        val o=obj(body)
        if (o["success"]?.toString()!="true" || o.string("response")!="success")
            throw DataFailure(when(o.string("response")) { "wrong-password"->"Incorrect password.";"unknown-user"->"Account not found. No account was created.";else->"Login was not accepted." })
        return Account(o.long("id")?.takeIf { it>0 } ?: throw DataFailure("Login response has no user ID."),o.string("name")?.takeIf { it.isNotBlank() } ?: throw DataFailure("Login response has no account name."))
    }
    fun success(body: String) {
        val o=obj(body)
        if(o["success"]?.toString()!="true") throw DataFailure(o.string("reason")?.take(300) ?: "The server rejected this action.")
    }
    fun vote(body: String,id: Long): VoteResult {
        success(body)
        val list=obj(body).getAsJsonArray("posts") ?: throw DataFailure("Vote response is missing the updated post.")
        val post=list.firstOrNull { it.isJsonObject && it.asJsonObject.long("id")==id }?.asJsonObject ?: throw DataFailure("Vote response refers to a different post.")
        val n=post.long("score")?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        return VoteResult(id,n)
    }
    fun currentVote(body: String): Int {
        success(body);return obj(body).long("vote")?.takeIf { it in 0L..3L }?.toInt() ?: throw DataFailure("Invalid current vote.")
    }
    fun comments(body: String): List<Comment> = try {
        val a=JsonParser.parseString(body);if(!a.isJsonArray)throw DataFailure("Invalid comment response.")
        a.asJsonArray.map { element ->
            val o=element.asJsonObject
            Comment(o.long("id")?.takeIf { it>0 } ?: throw DataFailure("Invalid comment ID."),
                o.long("post_id")?.takeIf { it>0 } ?: throw DataFailure("Invalid comment post ID."),
                o.string("creator"),o.long("creator_id"),o.string("body") ?: throw DataFailure("Missing comment body."),o.string("created_at"))
        }
    } catch(e: DataFailure) { throw e } catch(e: Exception) { throw DataFailure("Unable to parse comments.") }
    fun homeUserId(html:String):Long? {
        val menu=Jsoup.parse(html).selectFirst("#main-menu li.user") ?: throw DataFailure("Unable to verify the website session.")
        for(link in menu.select("a[href]")) {
            Regex("^/user/show/([0-9]+)(?:/.*)?$").matchEntire(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()?.takeIf{it>0}?.let{return it}
        }
        if(menu.select("a[href^=/user/login]").isNotEmpty())return null
        throw DataFailure("The website account menu could not be verified.")
    }
    fun csrf(html: String): String = Jsoup.parse(html).selectFirst("meta[name=csrf-token]")?.attr("content")?.takeIf { it.isNotBlank() }
        ?: throw DataFailure("The website did not provide a CSRF token. Open the official page to check access.")
    fun timecodes(body: String): List<Pair<String,Long>> = Regex("(?<![0-9:])([0-9]{1,3}):([0-5][0-9])(?:[.]([0-9]{1,3}))?(?![0-9:])").findAll(body).map { m ->
        m.value to (m.groupValues[1].toLong()*60000+m.groupValues[2].toLong()*1000+(m.groupValues[3]+"000").take(3).toLong())
    }.distinct().take(20).toList()
}
