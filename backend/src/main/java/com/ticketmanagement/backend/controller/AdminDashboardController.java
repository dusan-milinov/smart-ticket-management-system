package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.dashboard.AdminDashboardResponse;
import com.ticketmanagement.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboard;

    @GetMapping
    public AdminDashboardResponse getSummary() {
        return dashboard.getSummary();
    }
}
