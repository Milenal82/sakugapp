// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import kotlinx.coroutines.*

interface WebsiteFavoritesSource {suspend fun page(account:Account,grade:Int,page:Int):PostPage}
/** Search membership is verified against the authenticated per-post vote, never global Score. */
class WebsiteFavoritesRepository(
    private val posts:PostRepository,
    private val identity:suspend()->Account?,
    private val currentVote:suspend(Long)->Int,
):WebsiteFavoritesSource {
    override suspend fun page(account:Account,grade:Int,page:Int):PostPage=withContext(Dispatchers.IO){
        require(grade in 0..3 && page>0)
        if(identity()!=account)throw DataFailure("favorites_identity")
        if(account.name.any{it.isWhitespace() || it in ":\"'" || it.isISOControl()})throw DataFailure("favorites_identity")
        val level=if(grade==0)"1..3"else grade.toString()
        val result=posts.page("vote:$level:${account.name} order:id_desc",page)
        for(post in result.posts.distinctBy{it.id}){
            currentCoroutineContext().ensureActive()
            val vote=currentVote(post.id)
            if(vote !in 1..3 || (grade!=0 && vote!=grade))throw DataFailure("favorites_unverified")
        }
        if(identity()!=account)throw DataFailure("favorites_identity")
        result.copy(posts=result.posts.distinctBy{it.id})
    }
}
