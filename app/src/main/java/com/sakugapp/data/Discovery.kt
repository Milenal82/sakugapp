// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class EpisodeKind { EPISODE, OP, ED, MOVIE, OVA, PV, OTHER, MISSING }
data class EpisodeKey(val kind:EpisodeKind,val number:Int?=null){val key get()=kind.name.lowercase()+":"+(number ?: "")}
data class ParsedEpisode(val group:EpisodeKey,val source:String,val reliable:Boolean)
object SourceEpisodeParser {
    private val urls=Regex("(?:https?://|www\\.)\\S+",RegexOption.IGNORE_CASE)
    private val episode=Regex("(?i)(?:#\\s*|\\bep(?:isode)?\\.?\\s*)(0*\\d{1,4})(?![\\d\\p{L}]|\\.[0-9]|\\s*[x×])")
    private val special=Regex("(?i)\\b(opening|op|ending|ed|ova)(?:\\s*([0-9]{1,2}))?\\b")
    fun parse(source:String?):ParsedEpisode {
        val raw=source.orEmpty();if(raw.isBlank())return ParsedEpisode(EpisodeKey(EpisodeKind.MISSING),raw,false)
        val text=urls.replace(raw," ")
        val specials=special.findAll(text).map{m->EpisodeKey(when(m.groupValues[1].lowercase()){ "opening","op"->EpisodeKind.OP;"ending","ed"->EpisodeKind.ED;else->EpisodeKind.OVA},m.groupValues[2].toIntOrNull()?.takeIf{it>0})}.distinct().toList()
        if(specials.size==1)return ParsedEpisode(specials.single(),raw,true)
        if(specials.size>1)return ParsedEpisode(EpisodeKey(EpisodeKind.OTHER),raw,false)
        val numbers=episode.findAll(text).mapNotNull{it.groupValues[1].toIntOrNull()?.takeIf{n->n in 1..1899}}.distinct().toList()
        if(numbers.size==1)return ParsedEpisode(EpisodeKey(EpisodeKind.EPISODE,numbers.single()),raw,true)
        if(numbers.size>1)return ParsedEpisode(EpisodeKey(EpisodeKind.OTHER),raw,false)
        val kind=when{Regex("(?i)\\b(movie|film)\\b").containsMatchIn(text)->EpisodeKind.MOVIE;Regex("(?i)\\b(pv|trailer|teaser)\\b").containsMatchIn(text)->EpisodeKind.PV;else->EpisodeKind.OTHER}
        return ParsedEpisode(EpisodeKey(kind),raw,kind!=EpisodeKind.OTHER)
    }
    fun groups(posts:List<Post>)=posts.distinctBy{it.id}.groupBy{parse(it.source).group}.toSortedMap(compareBy<EpisodeKey>{it.kind.ordinal}.thenBy{it.number ?: 0})
}
data class TagFrequency(val tag:Tag,val count:Int,val percent:Double)
data class ArtistStatistics(val count:Int,val scoreCount:Int,val mean:Double?,val years:Map<Int,Int>,val missingYears:Int,val copyrights:List<TagFrequency>,val related:List<TagFrequency>)
object ArtistStatsCalculator {
    fun calculate(artist:String,input:List<Post>):ArtistStatistics {
        val posts=input.distinctBy{it.id};val scores=posts.mapNotNull{it.score};val years=posts.mapNotNull{p->p.createdAt?.takeIf{it in 1..4102444800L}?.let{Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year}}
        val tags=posts.flatMap{it.tags.distinctBy{t->t.name}}.filter{it.name!=artist}.groupBy{it.name}.map{(_,ts)->
            val types=ts.map{it.category}.distinct();val tag=ts.first().copy(category=types.singleOrNull() ?: TagCategory.UNKNOWN)
            TagFrequency(tag,ts.size,if(posts.isEmpty())0.0 else ts.size*100.0/posts.size)
        }.sortedWith(compareByDescending<TagFrequency>{it.count}.thenBy{it.tag.name})
        return ArtistStatistics(posts.size,scores.size,if(scores.isEmpty())null else scores.sumOf{it.toLong()}.toDouble()/scores.size,years.groupingBy{it}.eachCount().toSortedMap(),posts.size-years.size,tags.filter{it.tag.category==TagCategory.COPYRIGHT},tags)
    }
}
data class Sample(val tag:String,val type:TagCategory,val posts:List<Post> = emptyList(),val pages:Int=0,val ended:Boolean=false,val capped:Boolean=false,val warning:String?=null,val savedAt:Long=0)
/** One bounded classification summary, eight ten-minute samples, sequential requests of <=100 posts. */
class DiscoveryRepository(private val repository:PostRepository=SakugaRepository(pageSize=100),private val catalog:suspend()->Map<String,TagCategory> = { (repository as SakugaRepository).classifications() },private val clock:()->Long={System.currentTimeMillis()}){
    private val samples=LinkedHashMap<String,Sample>(8,.75f,true)
    private val mutex=Mutex()
    suspend fun tags(query:String,type:TagCategory):List<TagSuggestion> = withContext(Dispatchers.Default){
        if(query.trim().length<2)return@withContext emptyList()
        val normalized=query.trim().replace(Regex("\\s+"),"_");val types=catalog();types.entries.asSequence().filter{it.value==type && it.key.contains(normalized,true)}.sortedWith(compareBy<Map.Entry<String,TagCategory>>{!it.key.startsWith(normalized,true)}.thenBy{it.key}).take(30).map{TagSuggestion(it.key,it.value,null)}.toList()
    }
    suspend fun validate(tag:String,type:TagCategory){if(!LibraryCodec.tagName(tag) || catalog()[tag]!=type)throw DataFailure("Tag classification is not verified.")}
    fun cached(tag:String,type:TagCategory):Sample?=synchronized(samples){val key=samples.entries.lastOrNull{it.value.tag==tag && it.value.type==type}?.key;key?.let{samples[it]}?.takeIf{clock()-it.savedAt<600000}}
    suspend fun load(tag:String,type:TagCategory,pages:Int=1,refresh:Boolean=false,update:(Sample)->Unit={}):Sample=withContext(Dispatchers.IO){mutex.withLock{
        validate(tag,type)
        var sample=if(refresh)null else cached(tag,type)
        sample=sample ?: Sample(tag,type)
        if(refresh)synchronized(samples){samples.entries.removeAll{it.value.tag==tag && it.value.type==type}}
        val limit=if(type==TagCategory.ARTIST)5 else 12
        repeat(pages.coerceIn(1,3)){
            currentCoroutineContext().ensureActive()
            if(!sample!!.ended && sample!!.pages<limit){
                val page=repository.page(tag,sample!!.pages+1)
                currentCoroutineContext().ensureActive()
                if(page.posts.any{post->post.tags.none{t->t.name==tag}})throw DataFailure("The server returned posts for a different tag.")
                val old=sample!!;val merged=(old.posts+page.posts).distinctBy{it.id}.take(limit*repository.pageSize)
                sample=Sample(tag,type,merged,old.pages+1,page.rawCount==0,old.pages+1>=limit,page.categoryWarning,clock())
                synchronized(samples){samples.entries.removeAll{it.value.tag==tag && it.value.type==type};samples["$type:$tag:${sample!!.pages}"]=sample!!;while(samples.size>8)samples.remove(samples.keys.first())}
                update(sample!!)
            }
        };sample!!
    }}
}
