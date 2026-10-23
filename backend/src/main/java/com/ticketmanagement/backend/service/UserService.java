package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.auth.ChangePasswordRequest;
import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.LoginResponse;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.dto.auth.UserResponse;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.exception.PasswordChangeException;
import com.ticketmanagement.backend.exception.UserAlreadyExistsException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        return createAccount(request.email(), request.password(), Role.USER);
    }

    /** Creates an agent account for an administrator without initiating a verification or invitation flow. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse registerSupportAgent(@Valid RegisterSupportAgentRequest request) {
        return createAccount(request.email(), request.password(), Role.SUPPORT_AGENT);
    }

    /** Changes only the authenticated account's password; issued JWTs remain valid until expiration. */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void changeOwnPassword(@Valid ChangePasswordRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new PasswordChangeException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new PasswordChangeException("New password must differ from the current password");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private UserResponse createAccount(String suppliedEmail, String password, Role role) {
        String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
        if(userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }

        String encodedPassword = passwordEncoder.encode(password);

        AppUser user = new AppUser(
                email,
                encodedPassword,
                role
        );

        AppUser savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        request.password()
                )
        );

        String token = jwtService.generateToken(authentication);
        return new LoginResponse(token);

    }



}
