package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.LoginResponse;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.UserResponse;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.exception.UserAlreadyExistsException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserResponse register(RegisterRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        if(userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        AppUser user = new AppUser(
                email,
                encodedPassword,
                Role.USER

        );

        AppUser savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication aithentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        request.password()
                )
        );

        String token = jwtService.generateToken(aithentication);
        return new LoginResponse(token);

    }



}
