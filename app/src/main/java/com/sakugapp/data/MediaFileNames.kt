// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.net.URI
import java.util.Locale

/** A display name, never a path or an authentication-bearing URL. */
object MediaFileNames {
    const val MAX_BYTES=180
    private val urls=Regex("(?i)(?:https?://|www\\.)\\S+")
    private fun clean(value:String)=value.replace(Regex("[\\p{Cc}\\p{Cf}<>:\"/\\\\|?*]")," ")
        .replace(Regex("\\s+")," ").trim(' ','.')
    private fun limit(value:String,bytes:Int):String {
        val out=StringBuilder();var used=0
        value.codePoints().toArray().forEach{point->val part=String(Character.toChars(point));val size=part.toByteArray(Charsets.UTF_8).size
            if(used+size>bytes)return out.toString().trimEnd(' ','.');out.append(part);used+=size}
        return out.toString().trimEnd(' ','.')
    }
    fun source(value:String?):String {
        val raw=value?.trim().orEmpty();if(raw.isBlank())return ""
        val uri=runCatching{URI(raw)}.getOrNull()
        if(raw.startsWith("https://",true) || raw.startsWith("http://",true)){
            // The hostname identifies the source without leaking credentials, signed paths or query values.
            val authority=uri?.rawAuthority ?: return ""
            val host=uri.host ?: runCatching{java.net.IDN.toASCII(authority.substringAfterLast('@').substringBefore(':'))}.getOrNull() ?: return ""
            return limit(clean(host.lowercase().removePrefix("www.")),72)
        }
        // Text sources can also carry accidental URL credentials; discard assignments and query/fragment suffixes.
        val withoutUrls=urls.replace(raw){match->source(if(match.value.startsWith("www.",true))"https://"+match.value else match.value)}
        val text=withoutUrls.substringBefore('?').substringBefore('#').replace(Regex("(?i)(?:token|auth|session|password|secret|api[_-]?key|signature|credential)\\s*[:=]\\s*\\S+"),"")
        return limit(clean(text),72)
    }
    /** Only explicit episode markers count. URL IDs, query values and fragments are not episodes. */
    fun episode(value:String?):String? {
        val text=urls.replace(value.orEmpty()){match->
            val uri=runCatching{URI(if(match.value.startsWith("www.",true))"https://"+match.value else match.value)}.getOrNull()
            uri?.path.orEmpty().replace('#',' ')
        }.replace(Regex("(?i)(?:token|auth|session|password|secret|api[_-]?key|signature|credential)\\s*[:=]\\s*\\S+"),"")
        val suffix="(?![\\p{L}\\p{N}]|\\s*(?:[-–./,:_]+|&|(?i:and|to))\\s*[0-9])"
        val markers=listOf(
            Regex("(?i)(?<![\\p{L}\\p{N}_])(?:episode|ep)[\\s._-]*([0-9]{1,4})$suffix"),
            Regex("(?<![#/0-9])#[ \\t]*([0-9]{1,4})$suffix")
        )
        val numbers=markers.flatMap{it.findAll(text).map{match->match.groupValues[1].toInt()}.toList()}.distinct()
        return numbers.singleOrNull()?.let{"ep_$it"}
    }
    fun forPost(post:Post,extension:String):String {
        require(post.id>0 && extension.lowercase(Locale.ROOT) in setOf("mp4","webm","mkv"))
        fun part(value:String)=value.replace(Regex("\\s+"),"_")
        val artists=post.tags.filter{it.category==TagCategory.ARTIST}.map{part(clean(it.name))}.filter{it.isNotBlank()}.distinct().joinToString("__")
        val artist=limit(artists,64).ifBlank{"unknown_artist"}
        val origin=episode(post.source) ?: part(source(post.source)).ifBlank{"unknown_source"}
        return "${artist}_${origin}_${post.id}."+extension.lowercase(Locale.ROOT)
    }
    fun unique(name:String,exists:(String)->Boolean):String {
        require(name.toByteArray().size<=MAX_BYTES && !name.contains('/') && !name.contains('\\'))
        if(!exists(name))return name
        val ext=name.substringAfterLast('.');val stem=name.substringBeforeLast('.')
        for(index in 2..10000){
            val suffix=" ($index).$ext";val candidate=limit(stem,MAX_BYTES-suffix.toByteArray().size)+suffix
            if(!exists(candidate))return candidate
        }
        throw LibraryFailure("media_limit")
    }
}
