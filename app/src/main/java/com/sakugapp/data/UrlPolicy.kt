// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlPolicy {
    private val site=setOf("www.sakugabooru.com","sakugabooru.com")
    fun trusted(value:String):Boolean {
        val u=value.toHttpUrlOrNull() ?: return false
        if(!u.isHttps || u.username.isNotEmpty() || u.password.isNotEmpty() || u.port!=443)return false
        return u.host in site || u.host=="blog.sakugabooru.com"
    }
    fun postId(value:String):Long? {
        val u=value.toHttpUrlOrNull() ?: return null
        if(!trusted(value) || u.host !in site)return null
        return Regex("^/post/show/([0-9]+)(?:/.*)?$").matchEntire("/"+u.pathSegments.joinToString("/"))?.groupValues?.get(1)?.toLongOrNull()?.takeIf{it>0}
    }
    fun isPosts(value:String):Boolean {
        val u=value.toHttpUrlOrNull() ?: return false
        return trusted(value) && u.host in site && (u.pathSegments.firstOrNull().orEmpty() in setOf("post","") && postId(value)==null)
    }
    fun tags(value:String):String = value.toHttpUrlOrNull()?.queryParameter("tags").orEmpty()
    fun localized(value:String,language:String):String {
        require(language in PreferenceRules.languages && trusted(value))
        val u=value.toHttpUrlOrNull()!!
        return if(u.host in site)u.newBuilder().setQueryParameter("locale",PreferenceRules.siteLanguage(language)).build().toString() else value
    }
}
