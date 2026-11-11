package com.animeflix.data

import com.animeflix.model.PlaybackServer
import org.junit.Assert.*
import org.junit.Test

class DownloadOriginPolicyTest {
    private val mp4 = PlaybackServer("SUB · MP4Upload", "https://www.mp4upload.com/embed-abc.html")
    private val voe = PlaybackServer("SUB · Voe", "https://voe.sx/e/abc")
    private val upn = PlaybackServer("SUB · UPNShare", "https://animeav1.uns.bio/#abc")
    @Test fun fiveChaptersRotateProvidersAndPreserveLanguage() {
        val all = listOf(mp4, voe, upn, voe.copy(name = "DUB · Voe"))
        var last: String? = null
        val chosen = (1..5).map {
            val next = DownloadOriginPolicy.candidates(all, mp4, last).first()
            assertEquals("SUB", DownloadOriginPolicy.language(next))
            DownloadOriginPolicy.provider(next).also { last = it }
        }
        assertEquals(listOf("MP4Upload", "Voe", "UPNShare", "MP4Upload", "Voe"), chosen)
    }
    @Test fun unavailableOrCoolingProvidersAreSkippedWithoutChangingAudio() {
        val all = listOf(mp4, voe, upn.copy(name = "DUB · UPNShare"))
        assertEquals(listOf(voe), DownloadOriginPolicy.candidates(all, mp4, "MP4Upload", setOf("MP4Upload")))
        assertTrue(DownloadOriginPolicy.candidates(all, mp4, null, setOf("MP4Upload", "Voe")).isEmpty())
    }
    @Test fun unknownLanguageOnlyUsesTheExplicitEmbedAndUnknownHostsAreRejected() {
        val unknown = mp4.copy(name = "Servidor")
        assertEquals(listOf(unknown), DownloadOriginPolicy.candidates(listOf(unknown, voe), unknown, null))
        assertNull(DownloadOriginPolicy.provider(voe.copy(embedUrl = "https://voe.sx.attacker.invalid/e/abc")))
        assertNull(DownloadOriginPolicy.provider(voe.copy(embedUrl = "http://voe.sx/e/abc")))
    }
    @Test fun slowSourcesOnlyRotateAfterWarmupAndWhenAnotherSourceExists() {
        assertFalse(DownloadOriginPolicy.shouldRotateSlowSource(59_999, 100_000_000, 1_000_000, 1f, true))
        assertTrue(DownloadOriginPolicy.shouldRotateSlowSource(60_000, 100_000_000, 1_000_000, 1f, true))
        assertFalse(DownloadOriginPolicy.shouldRotateSlowSource(60_000, 100_000_000, 1_000_000, 1f, false))
        assertFalse(DownloadOriginPolicy.shouldRotateSlowSource(60_000, 100_000_000, 10_000_000, 10f, true))
        assertTrue(DownloadOriginPolicy.shouldRotateSlowSource(60_000, -1, 1_000_000, 1f, true))
    }
}
