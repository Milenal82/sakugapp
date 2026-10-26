// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.JsonParser

object TagDictionary {
    fun parse(body:String,language:String):Map<String,String>{
        val entries=JsonParser.parseString(body).asJsonObject.getAsJsonObject("entries")
        return entries.entrySet().associate{(name,value)->name to value.asJsonObject.get(if(language=="zh-TW")"zh-TW"else"zh-CN").asString}
    }
    fun resolve(original:String,curated:Map<String,String>,custom:Map<String,String>)=custom[original] ?: curated[original]
}
