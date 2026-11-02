package it.unige.hidedroid.utils

import java.io.File
import java.util.zip.ZipFile

/**
 * Heuristic scan of an APK for TLS certificate pinning. Repackaging only makes the app trust the
 * user CA; pinned apps still reject it, so warn before the user waits for a repack that cannot help.
 * ponytail: substring match on raw dex/binary-XML bytes, no real parsing; false positives are
 * possible (library present but unused) and custom TrustManager pinning is not detected.
 */
object PinningDetector {

    private val DEX_MARKERS = listOf("okhttp3/CertificatePinner", "datatheorem/android/trustkit", "sha256/")
    // Binary XML stores strings as UTF-16LE (or UTF-8); match either.
    private const val NSC_MARKER = "pin-set"

    /** Human-readable reasons, empty when nothing suggests pinning. */
    fun detect(apk: File): List<String> {
        val reasons = linkedSetOf<String>()
        ZipFile(apk).use { zip ->
            for (entry in zip.entries()) {
                val name = entry.name
                val isDex = name.startsWith("classes") && name.endsWith(".dex")
                val isNsc = name.startsWith("res/xml/") && name.endsWith(".xml")
                if (!isDex && !isNsc) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (isDex) {
                    DEX_MARKERS.filter { contains(bytes, it.toByteArray()) }.forEach { reasons += it.trimEnd('/') }
                } else if (contains(bytes, NSC_MARKER.toByteArray()) ||
                        contains(bytes, NSC_MARKER.toByteArray(Charsets.UTF_16LE))) {
                    reasons += "network_security_config pin-set"
                }
            }
        }
        return reasons.toList()
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
