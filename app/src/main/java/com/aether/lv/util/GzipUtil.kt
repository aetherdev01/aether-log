package com.aether.lv.util

import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Utility untuk deteksi dan dekompresi stream GZIP (.gz / .log.gz).
 *
 * Deteksi pakai magic bytes 0x1F 0x8B — lebih reliable daripada cek ekstensi,
 * karena file bisa saja tidak punya ekstensi .gz tapi isinya compressed.
 */
object GzipUtil {

    /** GZIP magic bytes: 0x1F 0x8B */
    private const val MAGIC_BYTE_1 = 0x1F
    private const val MAGIC_BYTE_2 = 0x8B.toByte()

    /**
     * Cek apakah stream adalah GZIP dengan baca 2 byte pertama (magic bytes).
     * Stream harus support [InputStream.markSupported] = true agar bisa di-reset.
     * Gunakan [wrapIfNeeded] yang sudah handle ini secara otomatis.
     */
    fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 &&
        bytes[0] == MAGIC_BYTE_1.toByte() &&
        bytes[1] == MAGIC_BYTE_2

    /**
     * Wrap [inputStream] dengan [GZIPInputStream] jika terdeteksi sebagai GZIP,
     * atau kembalikan stream asli jika bukan.
     *
     * Cara kerja:
     * 1. Baca 2 byte pertama ke buffer
     * 2. Rekonstruksi stream dengan [java.io.SequenceInputStream] sehingga
     *    2 byte yang sudah dibaca tidak hilang
     * 3. Wrap dengan GZIPInputStream jika magic bytes cocok
     *
     * @param bufferSize Buffer size untuk GZIPInputStream (default 64KB)
     */
    fun wrapIfNeeded(inputStream: InputStream, bufferSize: Int = 65536): InputStream {
        // Baca 2 byte header
        val header = ByteArray(2)
        val read = inputStream.read(header)

        // Stream kosong atau terlalu pendek — kembalikan apa adanya
        if (read < 2) {
            return if (read <= 0) inputStream
            else java.io.SequenceInputStream(
                java.io.ByteArrayInputStream(header, 0, read),
                inputStream
            )
        }

        // Rekonstruksi stream: gabungkan 2 byte header + sisa stream
        val reconstructed = java.io.SequenceInputStream(
            java.io.ByteArrayInputStream(header),
            inputStream
        )

        return if (isGzip(header)) {
            GZIPInputStream(reconstructed, bufferSize)
        } else {
            reconstructed
        }
    }

    /**
     * Kembalikan nama file "dibersihkan" dari suffix .gz untuk display.
     * Contoh: "kernel.log.gz" → "kernel.log", "crash.gz" → "crash"
     */
    fun stripGzSuffix(name: String): String =
        if (name.endsWith(".gz", ignoreCase = true)) name.dropLast(3) else name
}
