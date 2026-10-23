package com.ticketmanagement.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class JwtCookieService {

    private final String cookieName;
    private final boolean secure;
    private final Duration maxAge;

    public JwtCookieService(
            @Value("${security.jwt.cookie-name}") String cookieName,
            @Value("${security.jwt.cookie-secure}") boolean secure,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.maxAge = Duration.ofSeconds(expirationSeconds);
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(maxAge).build();
    }

    // The browser cannot delete an HttpOnly cookie, so the server expires it.
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }
}
