package com.ticketmanagement.backend.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** Login principal carrying the account's token version so issued JWTs can embed it. */
@Getter
public class AppUserPrincipal extends User {

    private final long tokenVersion;

    public AppUserPrincipal(String username, String password, boolean enabled,
                            Collection<? extends GrantedAuthority> authorities, long tokenVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }
}
