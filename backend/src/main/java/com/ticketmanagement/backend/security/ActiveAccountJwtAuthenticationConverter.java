package com.ticketmanagement.backend.security;

import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Accepts a signature-valid JWT while its account exists, is active, and still has the token
 * version embedded at login. This costs one account lookup per authenticated request, but makes
 * deactivation and password changes effective before the token expires. Rejections are displayed as 401.
 */
@NullMarked
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final JwtAuthenticationConverter delegate;

    public ActiveAccountJwtAuthenticationConverter(UserRepository userRepository, JwtAuthenticationConverter delegate) {
        this.userRepository = userRepository;
        this.delegate = delegate;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = userRepository.findByEmail(jwt.getSubject())
                .orElseThrow(() -> new InvalidBearerTokenException("Token account is unavailable"));
        if (!user.isActive()) {
            throw new DisabledException("Account is inactive");
        }
        Object version = jwt.getClaim(JwtService.TOKEN_VERSION_CLAIM);
        if (!(version instanceof Number number) || number.longValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token has been revoked");
        }
        return delegate.convert(jwt);
    }
}
