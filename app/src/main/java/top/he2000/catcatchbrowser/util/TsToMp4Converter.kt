package top.he2000.catcatchbrowser.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 将 TS (MPEG-TS) 文件转换为 MP4 容器格式
 * 使用 Android 原生 MediaExtractor + MediaMuxer，无需外部依赖
 */
object TsToMp4Converter {

    sealed class Result {
        data object Success : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * 将 TS 文件转换为 MP4
     * @param tsFile 输入的 TS 文件
     * @param mp4File 输出的 MP4 文件
     * @return 转换结果
     */
    fun convert(tsFile: File, mp4File: File): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(tsFile.absolutePath)

            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                return Result.Error("未找到媒体轨道")
            }

            val muxer = MediaMuxer(mp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndexMap = mutableListOf<Pair<Int, Int>>() // extractor track -> muxer track

            // 添加所有轨道
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val muxerTrack = muxer.addTrack(format)
                trackIndexMap.add(i to muxerTrack)
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            val bufferInfo = MediaCodec.BufferInfo()

            // 逐轨道拷贝数据
            for ((extractorTrack, muxerTrack) in trackIndexMap) {
                extractor.selectTrack(extractorTrack)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    extractor.advance()
                }

                extractor.unselectTrack(extractorTrack)
            }

            muxer.stop()
            muxer.release()

            return Result.Success
        } catch (e: Exception) {
            // 转换失败时清理不完整的输出文件
            if (mp4File.exists()) mp4File.delete()
            return Result.Error(e.message ?: "转换失败")
        } finally {
            extractor.release()
        }
    }
}
