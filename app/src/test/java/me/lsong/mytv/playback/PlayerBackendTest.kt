package me.lsong.mytv.playback

import me.lsong.mytv.playback.media3.isSoftwareCodec

import me.lsong.mytv.playback.effectivePlayerBackend

import org.junit.Assert.*
import org.junit.Test

class PlayerBackendTest {
    @Test fun android7NeverLoadsMpv() {
        assertEquals("MEDIA3", effectivePlayerBackend("MPV", 24))
        assertEquals("MEDIA3", effectivePlayerBackend("MPV", 25))
        assertEquals("MPV", effectivePlayerBackend("MPV", 26))
        assertEquals("MEDIA3", effectivePlayerBackend("unknown", 34))
    }
    @Test fun softwareCodecRecognitionKeepsVendorHardwareOutOfSoftwarePriority() {
        assertTrue(isSoftwareCodec("OMX.google.h264.decoder"))
        assertTrue(isSoftwareCodec("c2.android.avc.decoder"))
        assertFalse(isSoftwareCodec("c2.amlogic.avc.decoder"))
    }
}
