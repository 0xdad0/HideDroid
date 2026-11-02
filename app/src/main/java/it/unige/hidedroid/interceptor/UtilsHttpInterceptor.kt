package it.unige.hidedroid.interceptor

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.netty.buffer.ByteBuf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

object UtilsHttpInterceptor {

    fun removeAllNonUtf8Char(original: String): String {
        val regex = Regex("[^\\n\\r\\t\\p{Print}]")
        return regex.replace(original, "")
    }

    fun fromMapToString(map: MutableMap<String?, List<String>?>?): String? {
        if (map == null) {
            return null
        }
        val gson = Gson()
        val type: Type = object : TypeToken<MutableMap<String?, List<String>?>>() {}.type
        return gson.toJson(map, type)
    }

    // Funzione utile ad eliminare i byte a 0 in eccesso
    fun trim(bytes: ByteArray, contentLength: Int): ByteArray? {
        var i = bytes.size - 1
        var limit = 0
        if (contentLength != 0) {
            limit = contentLength + 1
        }
        while (i >= limit && bytes[i].toInt() == 0) {
            --i
        }
        return bytes.copyOf(i + 1)
    }

    // Funzione per decomprimere pacchetti GZIP e DEFLATE
    fun decompressContents(fullMessage: ByteArray, type: String, packageNameApp: String, host: String, headers: String, isDebugEnabled: AtomicBoolean): ByteArray? {
        var fullMessage = fullMessage
        var reader: InflaterInputStream? = null
        val uncompressed: ByteArrayOutputStream
        try {
            if (type.equals("gzip", ignoreCase = true)) {
                reader = GZIPInputStream(ByteArrayInputStream(fullMessage))
            } else if (type.equals("deflate", ignoreCase = true)) {
                reader = InflaterInputStream(ByteArrayInputStream(fullMessage))
            }
            uncompressed = ByteArrayOutputStream(fullMessage.size)
            val decompressBuffer = ByteArray(HttpInterceptor.DECOMPRESS_BUFFER_SIZE)
            var bytesRead: Int
            while (reader!!.read(decompressBuffer).also { bytesRead = it } > -1) {
                uncompressed.write(decompressBuffer, 0, bytesRead)
            }
            fullMessage = uncompressed.toByteArray()
        } catch (e: IOException) {
            if (isDebugEnabled.get()) {
                Log.e("errorDecompressingContent", "Unable to decompress request: $e")
            }
            Log.w(HttpInterceptor.TAG, "Unable to decompress request", e)
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                Log.w(HttpInterceptor.TAG, "Unable to close gzip stream", e)
            }
        }
        return fullMessage
    }

    // TODO: capire perchè non funziona correttamente
    fun extractReadableBytes(content: ByteBuf): ByteArray? {
        val binaryContent = ByteArray(content.readableBytes())
        content.markReaderIndex()
        content.readBytes(binaryContent)
        content.resetReaderIndex()
        return binaryContent
    }

    /** HTTP/2 RST_STREAM(REFUSED_STREAM) frame: 9-byte header (len=4, type=3, flags=0, stream id) + error code 7. */
    fun rstStreamFrame(streamId: Int): ByteArray = byteArrayOf(
            0, 0, 4, 3, 0,
            (streamId ushr 24 and 0x7f).toByte(), (streamId ushr 16).toByte(), (streamId ushr 8).toByte(), streamId.toByte(),
            0, 0, 0, 7)
}
