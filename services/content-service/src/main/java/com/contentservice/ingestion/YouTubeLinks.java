package com.contentservice.ingestion;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure URL parsing, kept separate from ingestion so it can be tested without any dependency. */
public final class YouTubeLinks {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final List<String> WATCH_HOSTS =
            List.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com");
    private static final List<String> SHORT_HOSTS = List.of("youtu.be", "www.youtu.be");

    private YouTubeLinks() {
    }

    /** @return the 11-character video id, or empty if this is not a recognisable YouTube video URL. */
    public static Optional<String> videoId(URI url) {
        if (url == null || url.getHost() == null) {
            return Optional.empty();
        }
        String scheme = url.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return Optional.empty();
        }
        String host = url.getHost().toLowerCase();
        String path = url.getPath() == null ? "" : url.getPath();

        if (SHORT_HOSTS.contains(host)) {
            return validated(trimLeadingSlash(path));
        }
        if (WATCH_HOSTS.contains(host)) {
            if (path.equals("/watch")) {
                return validated(queryParam(url.getQuery(), "v"));
            }
            if (path.startsWith("/shorts/")) {
                return validated(path.substring("/shorts/".length()));
            }
            if (path.startsWith("/embed/")) {
                return validated(path.substring("/embed/".length()));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validated(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String id = candidate.split("/", 2)[0];
        return VIDEO_ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
    }

    private static String trimLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String queryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length == 2 && parts[0].equals(name))
                .map(parts -> parts[1])
                .findFirst()
                .orElse(null);
    }
}
