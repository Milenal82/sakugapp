// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.media.MediaMuxer
import android.media.MediaCodec
import androidx.media3.common.*
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.transformer.Muxer
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/** Media3 has an MP4 muxer; platform WebM writing preserves VP8/VP9/Vorbis/Opus samples. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class WebmClipMuxer(path:String):Muxer{
    private val muxer=MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
    private var started=false
    override fun addTrack(format:Format):Int=muxer.addTrack(MediaFormatUtil.createMediaFormatFromFormat(format))
    override fun writeSampleData(trackId:Int,data:ByteBuffer,info:MediaCodec.BufferInfo){if(!started){muxer.start();started=true};muxer.writeSampleData(trackId,data,info)}
    override fun addMetadataEntry(entry:Metadata.Entry){}
    override fun close(){try{if(started)muxer.stop()}finally{muxer.release()}}
    class Factory:Muxer.Factory{
        override fun create(path:String):Muxer=WebmClipMuxer(path)
        override fun getSupportedSampleMimeTypes(type:Int):ImmutableList<String> = ImmutableList.copyOf(if(type==C.TRACK_TYPE_VIDEO)listOf(MimeTypes.VIDEO_VP8,MimeTypes.VIDEO_VP9)else listOf(MimeTypes.AUDIO_VORBIS,MimeTypes.AUDIO_OPUS))
    }
}
