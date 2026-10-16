package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.UserResponse;
import com.ticketmanagement.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse response = userService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);

    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request
    ) {
        userService.login(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Login successful");
    }

}
