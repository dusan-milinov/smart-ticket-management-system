package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.dto.auth.UserResponse;
import com.ticketmanagement.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<UserResponse> registerSupportAgent(
            @Valid @RequestBody RegisterSupportAgentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerSupportAgent(request));
    }
}
