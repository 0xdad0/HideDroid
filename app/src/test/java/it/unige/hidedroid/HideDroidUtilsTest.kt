package it.unige.hidedroid

import it.unige.hidedroid.interceptor.UtilsHttpInterceptor
import it.unige.hidedroid.utils.PinningDetector
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HideDroidUtilsTest {

    @Test fun trimDropsTrailingZeros() {
        val body = "a=1&b=2".toByteArray()
        assertArrayEquals(body, UtilsHttpInterceptor.trim(body + ByteArray(32), 0))
    }

    @Test fun gzipRoundTrip() {
        val plain = "event=open&user=42".toByteArray()
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(plain) }
        val out = UtilsHttpInterceptor.decompressContents(bos.toByteArray(), "gzip", "pkg", "host", "{}", AtomicBoolean(false))
        assertArrayEquals(plain, out)
    }

    @Test fun rstStreamFrameLayout() {
        val f = UtilsHttpInterceptor.rstStreamFrame(0x01020304)
        assertArrayEquals(byteArrayOf(0, 0, 4, 3, 0, 1, 2, 3, 4, 0, 0, 0, 7), f)
    }

    @Test fun pinningDetectedInDex() {
        val apk = File.createTempFile("pin", ".apk").apply { deleteOnExit() }
        ZipOutputStream(apk.outputStream()).use {
            it.putNextEntry(ZipEntry("classes.dex")); it.write("xxLokhttp3/CertificatePinner;xx".toByteArray()); it.closeEntry()
        }
        assertEquals(listOf("okhttp3/CertificatePinner"), PinningDetector.detect(apk))
    }

    @Test fun noPinningInCleanApk() {
        val apk = File.createTempFile("clean", ".apk").apply { deleteOnExit() }
        ZipOutputStream(apk.outputStream()).use {
            it.putNextEntry(ZipEntry("classes.dex")); it.write("nothing here".toByteArray()); it.closeEntry()
        }
        assertTrue(PinningDetector.detect(apk).isEmpty())
    }
}
