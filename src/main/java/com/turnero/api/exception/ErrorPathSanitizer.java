package com.turnero.api.exception;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/** Error paths are diagnostic only: ambiguous input must never echo a credential. */
public final class ErrorPathSanitizer {
    private static final String CANCELLATION_PATH = "/api/v1/public/cancellations/{token}";
    private static final String REDACTED_PATH = "/{redacted}";
    private static final int MAX_DECODE_PASSES = 5;
    private static final Pattern INVALID_ESCAPE = Pattern.compile("%(?![0-9a-fA-F]{2})");
    private static final Pattern CANCELLATION = Pattern.compile(
            "(?:^|/)api/v1/public/cancellations(?:/|$)", Pattern.CASE_INSENSITIVE);

    private ErrorPathSanitizer() {}

    public static String sanitize(String path) {
        if (path == null) {
            return null;
        }
        String candidate = path;
        boolean ambiguous = false;
        for (int pass = 0; pass < MAX_DECODE_PASSES; pass++) {
            String segments = normalize(candidate, false);
            String normalized = normalize(candidate, true);
            // Check both forms: ".." may also remove a cancellation segment.
            if (CANCELLATION.matcher(segments).find() || CANCELLATION.matcher(normalized).find()) {
                return CANCELLATION_PATH;
            }
            ambiguous |= !segments.equals(normalized) || candidate.indexOf('\\') >= 0
                    || candidate.indexOf('?') >= 0 || candidate.indexOf('#') >= 0
                    || candidate.chars().anyMatch(c -> c < 32 || c == 127);
            if (candidate.indexOf('%') < 0) {
                return ambiguous ? REDACTED_PATH : path;
            }
            try {
                candidate = UriUtils.decode(candidate, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // A malformed token suffix must not hide an encoded route prefix.
                String escaped = INVALID_ESCAPE.matcher(candidate).replaceAll("%25");
                if (escaped.equals(candidate)) {
                    return REDACTED_PATH;
                }
                candidate = UriUtils.decode(escaped, StandardCharsets.UTF_8);
                ambiguous = true;
            }
        }
        // Excessive/nested encoding is not useful diagnostic data.
        return REDACTED_PATH;
    }

    private static String normalize(String value, boolean resolveDots) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : value.replace('\\', '/').split("/", -1)) {
            int matrix = segment.indexOf(';');
            if (matrix >= 0) {
                segment = segment.substring(0, matrix);
            }
            if (segment.isEmpty()) {
                continue;
            }
            if (resolveDots && segment.equals(".")) {
                continue;
            }
            if (resolveDots && segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.addLast(segment);
        }
        return "/" + String.join("/", segments);
    }
}
