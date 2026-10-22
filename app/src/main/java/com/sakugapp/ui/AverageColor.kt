// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*

/** Average only images obtained from real URLs; failed images do not contribute placeholder pixels. */
suspend fun averagePreviewColor(context:Context,urls:List<String>):Pair<String,Int> = withContext(Dispatchers.IO){
    var red=0L;var green=0L;var blue=0L;var pixels=0L;var loaded=0
    for(url in urls.distinct().take(8)){
        ensureActive()
        val result=Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(32).build())
        if(result is SuccessResult){
            val bitmap=result.drawable.toBitmap(32,32)
            for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){val c=bitmap[x,y];if(Color.alpha(c)>0){red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c);pixels++}}
            loaded++
        }
    }
    check(pixels>0){"No thumbnails could be read."}
    "#%02X%02X%02X".format(red/pixels,green/pixels,blue/pixels) to loaded
}
