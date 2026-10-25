package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.auth.AdminUserResponse;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @PostMapping("/support-agents")
    public ResponseEntity<AdminUserResponse> registerSupportAgent(
            @Valid @RequestBody RegisterSupportAgentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerSupportAgent(request));
    }

    /** Soft-deletes an account; repeated calls for an inactive account return 200 unchanged. */
    @PatchMapping("/users/{userId}/deactivate")
    public ResponseEntity<AdminUserResponse> deactivateUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.deactivateUser(userId));
    }
}
