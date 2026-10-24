package com.ticketmanagement.backend.security;

import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@NullMarked
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(()->
                        new UsernameNotFoundException("User with email " + email + " not found"));

        // Disabled accounts are rejected by DaoAuthenticationProvider before a token is issued.
        return new AppUserPrincipal(
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getTokenVersion());
    }

}
