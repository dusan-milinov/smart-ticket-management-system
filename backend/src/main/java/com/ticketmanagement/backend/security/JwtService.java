package com.ticketmanagement.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;


@Service
public class JwtService {

    public static final String TOKEN_VERSION_CLAIM = "token_version";

    private final JwtEncoder jwtEncoder;
    private final long expirationSeconds;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.expiration-seconds}")
            long expirationSeconds
    ) {
        this.jwtEncoder = jwtEncoder;
        this.expirationSeconds = expirationSeconds;
    }

    /** Issues a JWT for a successful login; the principal must carry the account's token version. */
    public String generateToken(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new IllegalStateException("Tokens can only be issued for application accounts");
        }
        Instant now = Instant.now();

        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ticket-management")
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .claim("roles", roles)
                .claim(TOKEN_VERSION_CLAIM, principal.getTokenVersion())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

}
