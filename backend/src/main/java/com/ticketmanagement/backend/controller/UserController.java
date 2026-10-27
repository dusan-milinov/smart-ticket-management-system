package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.auth.AdminUserResponse;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import org.springframework.web.bind.annotation.PathVariable;
import com.ticketmanagement.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public AdminUserResponse getUserDetails(@PathVariable long userId) {
        return userService.getUserDetails(userId);
    }

    @GetMapping("/{userId}/tickets")
    public Page<TicketResponse> getUserTickets(
            @PathVariable long userId,
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return userService.getUserTickets(userId, pageable);
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(
            Authentication authentication
    ) {
        // Spring Security also adds FACTOR_* authorities, so pick the ROLE_* one explicitly.
        String role = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("User has no role"));

        return Map.of(
                "email", authentication.getName(),
                "role", role
        );
    }

}
