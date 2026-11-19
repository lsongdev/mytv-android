package me.lsong.media.danmaku.internal

import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 弹幕源所需的纯算法原语（哈希/HMAC/AES/解压）。全部基于 ByteArray，无 Android/Base64 依赖，
 * 便于 JVM 单测；[DanmakuNet] 在外层封装供各原生源调用。
 */
internal object DanmakuPrimitives {

    fun md5(s: String): String = hex(MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)))

    fun sha1(s: String): String = hex(MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)))

    /** algo: md5 / sha1 / sha256 / hmacsha256…（含/不含 hmac 前缀均可）。返回十六进制。 */
    fun hmac(algo: String, msg: String, key: String): String = hex(hmacBytes(algo, msg, key))

    /** 原始 HMAC 字节（桥接层套 Base64，供需要 base64 签名的源用，如人人 x-ca-sign）。 */
    fun hmacRaw(algo: String, msg: String, key: String): ByteArray = hmacBytes(algo, msg, key)

    private fun hmacBytes(algo: String, msg: String, key: String): ByteArray {
        val a = algo.lowercase().removePrefix("hmac")
        val jce = when (a) {
            "md5" -> "HmacMD5"
            "sha1" -> "HmacSHA1"
            else -> "HmacSHA256"
        }
        val mac = Mac.getInstance(jce)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), jce))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
    }

    fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray =
        Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(data)
        }

    fun aesCbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)); doFinal(data)
        }

    /** CBC 解密但不去 PKCS7 填充（部分源用非标准末尾填充，由调用方自行 strip）。 */
    fun aesCbcDecryptNoPad(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)); doFinal(data)
        }

    fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)); doFinal(data)
        }

    /** raw=true → 无 zlib 头的 raw deflate（nowrap）；raw=false → zlib 包裹（含 78xx 头）。 */
    fun inflate(data: ByteArray, raw: Boolean): ByteArray {
        val inflater = Inflater(raw)
        try {
            inflater.setInput(data)
            val buf = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                require(out.size() + n <= 16_000_000) { "Inflated danmaku exceeds limit" }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally { inflater.end() }
    }

    fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(data.inputStream()).use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= 16_000_000) { "Inflated danmaku exceeds limit" }
            out.write(buffer,0,n)
        }
        out.toByteArray()
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
