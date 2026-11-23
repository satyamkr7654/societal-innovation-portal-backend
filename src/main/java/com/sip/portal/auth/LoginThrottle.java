package com.sip.portal.auth;

import com.sip.portal.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Slows password guessing. Kept in memory, so it suits a single server. */
@Component
class LoginThrottle {
    static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private record Entry(int failures, Instant since) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    synchronized void check(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        if (entry.since().plus(WINDOW).isBefore(Instant.now())) {
            entries.remove(key);
        } else if (entry.failures() >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again in 15 minutes.");
        }
    }

    synchronized void failed(String key) {
        Entry old = entries.get(key);
        entries.put(key, old == null ? new Entry(1, Instant.now()) : new Entry(old.failures() + 1, old.since()));
    }

    synchronized void succeeded(String key) {
        entries.remove(key);
    }
}
