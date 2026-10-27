package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.auth.AdminUserResponse;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/users")
    public Page<AdminUserResponse> getAllUsers(
            @ParameterObject
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return userService.getAllUsers(pageable);
    }

    @GetMapping("/support-agents")
    public Page<AdminUserResponse> getSupportAgents(
            @ParameterObject
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return userService.getSupportAgents(pageable);
    }

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
