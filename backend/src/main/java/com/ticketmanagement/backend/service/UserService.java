package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.auth.AdminUserResponse;
import com.ticketmanagement.backend.dto.auth.ChangePasswordRequest;
import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.LoginResponse;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.dto.auth.UserResponse;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.exception.PasswordChangeException;
import com.ticketmanagement.backend.exception.UserAlreadyExistsException;
import com.ticketmanagement.backend.exception.UserDeactivationException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.TicketRepository;
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
    private final TicketRepository ticketRepository;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        return UserResponse.from(createAccount(request.email(), request.password(), Role.USER));
    }

    /** Creates an agent account for an administrator without initiating a verification or invitation flow. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse registerSupportAgent(@Valid RegisterSupportAgentRequest request) {
        return AdminUserResponse.from(createAccount(request.email(), request.password(), Role.SUPPORT_AGENT));
    }

    /**
     * Changes the authenticated account's password and increments its token version, which
     * invalidates every JWT issued for the account, including the caller's. The caller must log in again.
     */
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
        user.incrementTokenVersion();
        userRepository.save(user);
    }

    /**
     * Soft-deletes a non-admin account: the row, its tickets, comments, and history are preserved,
     * while login and existing JWTs stop working. Repeating the call for an inactive account succeeds
     * without changes.
     *
     * @throws UserNotFoundException if no account has the given ID (404)
     * @throws UserDeactivationException if the target is an administrator (including the caller) or
     *         still has unresolved assigned tickets (409)
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse deactivateUser(long userId) {
        AppUser actor = currentUserService.getCurrentUser();
        // The row lock prevents a concurrent reassignment from slipping past the unresolved-ticket check.
        AppUser target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + userId + " not found."));
        if (target.getId().equals(actor.getId())) {
            throw new UserDeactivationException("Administrators cannot deactivate their own account");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new UserDeactivationException("Administrator accounts cannot be deactivated");
        }
        if (!target.isActive()) {
            return AdminUserResponse.from(target);
        }
        long unresolved = ticketRepository.countByAssigneeIdAndTicketStatusIn(userId, TicketStatus.UNRESOLVED);
        if (unresolved > 0) {
            throw new UserDeactivationException("User has " + unresolved
                    + " unresolved assigned ticket(s); reassign them before deactivation");
        }
        target.deactivate();
        userRepository.save(target);
        return AdminUserResponse.from(target);
    }

    private AppUser createAccount(String suppliedEmail, String password, Role role) {
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

        return userRepository.save(user);
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
