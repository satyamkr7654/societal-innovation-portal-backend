package com.sip.portal.auth;

import com.sip.portal.config.AppProperties;
import com.sip.portal.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

/** Issues and reads the signed session token, kept in a cookie that page scripts cannot read. */
@Component
public class SessionCookies {
    public static final String NAME = "sip_session";
    private static final String VERSION = "ver";

    public record Session(String userId, long version) {}

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties props;

    SessionCookies(JwtEncoder encoder, JwtDecoder decoder, AppProperties props) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.props = props;
    }

    public ResponseCookie issue(User user) {
        Instant now = Instant.now();
        Duration lifetime = Duration.ofHours(props.sessionHours());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId()).claim(VERSION, user.getSessionVersion()).issuedAt(now).expiresAt(now.plus(lifetime)).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return cookie(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), lifetime);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    /** The contents of a valid, unexpired token. */
    public Optional<Session> read(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (!NAME.equals(cookie.getName())) continue;
            try {
                Jwt jwt = decoder.decode(cookie.getValue());
                // Tokens from before this claim existed are refused
                return jwt.getSubject() != null && jwt.getClaim(VERSION) instanceof Long version
                        ? Optional.of(new Session(jwt.getSubject(), version)) : Optional.empty();
            } catch (JwtException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(props.cookieSecure())
                .sameSite("Strict").path("/api").maxAge(maxAge).build();
    }
}
