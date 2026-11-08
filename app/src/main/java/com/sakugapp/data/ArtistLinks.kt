// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

object ArtistLinks {
    fun candidates(posts:List<Post>):List<TagFrequency> =
        ArtistStatsCalculator.calculate("",posts).related.filter{it.tag.category==TagCategory.ARTIST}

    fun query(show:String,artist:Tag):String {
        require(artist.category==TagCategory.ARTIST)
        return SearchSyntax.add(show,artist.name,"and")
    }
    /** Keep the actual parsed episode scope; remote source: matching is not exact. */
    fun episodePosts(posts:List<Post>,key:EpisodeKey,artist:Tag):List<Post> {
        require(artist.category==TagCategory.ARTIST)
        return posts.distinctBy{it.id}.filter{SourceEpisodeParser.parse(it.source).group==key &&
            it.tags.any{t->t.name==artist.name && t.category==TagCategory.ARTIST}}
    }
    fun selectedQuery(names:List<String>):String = names.distinct().filter{LibraryCodec.tagName(it)}.joinToString(" ")
}
