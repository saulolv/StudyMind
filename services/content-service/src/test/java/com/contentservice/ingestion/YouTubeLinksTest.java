package com.contentservice.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeLinksTest {

    @ParameterizedTest
    @CsvSource({
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://m.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s, dQw4w9WgXcQ",
        "https://www.youtube.com/watch?list=PL123&v=dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ?t=42, dQw4w9WgXcQ",
        "https://www.youtube.com/shorts/dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://www.youtube.com/embed/dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=_-aBcD12345, _-aBcD12345"
    })
    void extractsVideoIdFromEveryAcceptedUrlShape(String url, String expectedId) {
        assertThat(YouTubeLinks.videoId(URI.create(url))).contains(expectedId);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://vimeo.com/123456789",
                "https://www.youtube.com/watch?v=tooshort",
                "https://www.youtube.com/watch?v=waytoolongvideoid",
                "https://www.youtube.com/feed/subscriptions",
                "https://www.youtube.com/watch",
                "https://evil.com/youtube.com/watch?v=dQw4w9WgXcQ",
                "ftp://youtu.be/dQw4w9WgXcQ",
                // A relative URI parses fine but has no host; a string that is not a URI at all is
                // rejected by URI.create before it reaches here, which the controller maps to 400.
                "watch?v=dQw4w9WgXcQ"
            })
    void rejectsAnythingThatIsNotAYouTubeVideo(String url) {
        assertThat(YouTubeLinks.videoId(URI.create(url))).isEmpty();
    }

    @Test
    void rejectsANullUrl() {
        assertThat(YouTubeLinks.videoId(null)).isEmpty();
    }

    /** Parses as a URI with a host but no scheme, which is not something this service will fetch. */
    @Test
    void rejectsASchemeRelativeUrl() {
        assertThat(YouTubeLinks.videoId(URI.create("//www.youtube.com/watch?v=dQw4w9WgXcQ"))).isEmpty();
    }

    @Test
    void ignoresHostCasing() {
        assertThat(YouTubeLinks.videoId(URI.create("https://WWW.YouTube.com/watch?v=dQw4w9WgXcQ")))
                .contains("dQw4w9WgXcQ");
    }

    /** A query with a bare flag has a pair without "=", which must not blow up the parameter scan. */
    @Test
    void toleratesAMalformedQueryString() {
        assertThat(YouTubeLinks.videoId(URI.create("https://www.youtube.com/watch?embedded&v=dQw4w9WgXcQ")))
                .contains("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://youtu.be/", "https://www.youtube.com/shorts/"})
    void rejectsUrlsWithNoVideoId(String url) {
        assertThat(YouTubeLinks.videoId(URI.create(url))).isEmpty();
    }
}
