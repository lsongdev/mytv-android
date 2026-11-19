package me.lsong.media.danmaku.internal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import java.util.zip.GZIPOutputStream

class DanmakuPrimitivesTest {

    @Test
    fun md5KnownVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", DanmakuPrimitives.md5("abc"))
    }

    @Test
    fun hmacSha256KnownVector() {
        assertEquals(
            "5031fe3d989c6d1537a013fa6e739da23463fdaec3b70137d828e36ace221bd0",
            DanmakuPrimitives.hmac("sha256", "data", "key"),
        )
    }

    @Test
    fun aesEcbPkcs7KnownVector() {
        // 由 openssl 生成：AES-128-ECB-PKCS7，key="0123456789abcdef"，pt="hello danmaku world"
        val ct = Base64.getDecoder().decode("e9YOhSr8/IxeE4hTOrB6IfiibS2sVvRudZa67og8KKY=")
        val pt = DanmakuPrimitives.aesEcbDecrypt(ct, "0123456789abcdef".toByteArray())
        assertEquals("hello danmaku world", String(pt, Charsets.UTF_8))
    }

    @Test
    fun gunzipRoundtrip() {
        val original = "弹幕 danmaku 测试".toByteArray(Charsets.UTF_8)
        val gz = java.io.ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(original) } }.toByteArray()
        assertArrayEquals(original, DanmakuPrimitives.gunzip(gz))
    }

    @Test
    fun inflateRawAndZlib() {
        val original = "<i><d p=\"1.0,1,25,16777215\">hi</d></i>".toByteArray(Charsets.UTF_8)
        // zlib 包裹（Deflater 默认带头）
        val zlib = java.io.ByteArrayOutputStream().also {
            java.util.zip.DeflaterOutputStream(it).use { d -> d.write(original) }
        }.toByteArray()
        assertArrayEquals(original, DanmakuPrimitives.inflate(zlib, raw = false))
    }
}
