package com.animeflix.data

import org.junit.Assert.*
import org.junit.Test

class Mp4UploadResolverTest {
    @Test fun readsPublishedVideoConfigurationIncludingProviderPort() {
        val url = "https://a4.mp4upload.com:183/d/example/video.mp4"
        assertEquals(url, Mp4UploadResolver.parse("""player.src({type:"video/mp4",src:"$url"});"""))
        assertTrue(Mp4UploadResolver.supports("https://www.mp4upload.com/embed-example123.html"))
    }
    @Test fun rejectsOtherHostsCredentialsAndNonVideoLinks() {
        for(url in listOf("http://a4.mp4upload.com/video.mp4", "https://mp4upload.com.evil.invalid/video.mp4",
            "https://user:secret@a4.mp4upload.com/video.mp4", "https://a4.mp4upload.com/image.jpg")) {
            assertNull(Mp4UploadResolver.parse("""player.src({src:"$url"});"""))
        }
        assertNull(Mp4UploadResolver.parse("<video></video>"))
        assertFalse(Mp4UploadResolver.supports("https://www.mp4upload.com.evil.invalid/embed-example.html"))
    }
}
