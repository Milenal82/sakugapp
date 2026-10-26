// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

data class SearchEdit(val text:String,val cursor:Int)
object SearchEditing {
    private fun bounds(text:String,cursor:Int):IntRange {
        require(cursor in 0..text.length)
        var start=cursor;var end=cursor
        while(start>0 && !text[start-1].isWhitespace())start--
        while(end<text.length && !text[end].isWhitespace())end++
        return start until end
    }
    fun prefix(text:String,cursor:Int):String? {
        val range=bounds(text,cursor)
        val token=text.substring(range.first,range.last+1)
        if(':' in token || '*' in token)return null
        return text.substring(range.first,cursor).removePrefix("-").removePrefix("~")
            .takeIf{it.length in 2..100 && it.none{c->c==':' || c=='*'}}
    }
    fun complete(text:String,cursor:Int,name:String):SearchEdit {
        require(name.isNotBlank() && name.none(Char::isWhitespace) && !name.startsWith('-') && !name.startsWith('~'))
        if(prefix(text,cursor)==null)return SearchEdit(text,cursor)
        val range=bounds(text,cursor);val end=range.last+1
        val token=text.substring(range.first,end)
        val operator=token.firstOrNull()?.takeIf{it=='-' || it=='~'}?.toString().orEmpty()
        val before=text.substring(0,range.first)+operator+name
        val after=text.substring(end)
        return if(after.firstOrNull()?.isWhitespace()==true)SearchEdit(before+after,before.length+1)
            else SearchEdit(before+" "+after,before.length+1)
    }
}
