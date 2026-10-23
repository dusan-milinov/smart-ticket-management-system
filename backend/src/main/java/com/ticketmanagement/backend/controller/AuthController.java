package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.ChangePasswordRequest;
import com.ticketmanagement.backend.dto.auth.LoginResponse;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.UserResponse;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final JwtCookieService jwtCookieService;

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(request);
        return ResponseEntity.noContent().build();
    }

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
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest request
    ) {
        String token = userService.login(request).token();
        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.create(token).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.clear().toString())
                .build();
    }

}
