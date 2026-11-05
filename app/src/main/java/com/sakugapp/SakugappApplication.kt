// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class SakugappApplication:Application(),ImageLoaderFactory {
    override fun onCreate(){super.onCreate();java.util.concurrent.Executors.newSingleThreadExecutor().apply{execute{com.sakugapp.data.PageVideoCache.cleanOrphans(this@SakugappApplication)};shutdown()}}
    override fun newImageLoader():ImageLoader = ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizeBytes(minOf(48L*1024*1024,
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass.toLong()*1024*1024*15/100).toInt()).build() }
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(128L*1024*1024).build() }
        .components {
        if(Build.VERSION.SDK_INT>=28)add(ImageDecoderDecoder.Factory())else add(GifDecoder.Factory())
    }.build()
}
