package com.cloudcostai.auth.controller;

import com.cloudcostai.auth.dto.*;
import com.cloudcostai.auth.service.interfaces.AuthService;
import com.cloudcostai.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }
    @PostMapping("/register") public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) { try { return ResponseEntity.ok(new ApiResponse<>(true, "Account created", authService.register(request))); } catch (IllegalArgumentException exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(false, exception.getMessage(), null)); } }
    @PostMapping("/login") public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) { try { return ResponseEntity.ok(new ApiResponse<>(true, "Signed in", authService.login(request))); } catch (IllegalArgumentException exception) { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(false, exception.getMessage(), null)); } }
    @GetMapping("/me") public ResponseEntity<ApiResponse<AuthResponse>> me(HttpServletRequest request) { try { return ResponseEntity.ok(new ApiResponse<>(true, "Session is active", authService.currentUser(bearerToken(request)))); } catch (IllegalArgumentException exception) { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(false, exception.getMessage(), null)); } }
    @PostMapping("/logout") public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) { authService.logout(bearerToken(request)); return ResponseEntity.ok(new ApiResponse<>(true, "Signed out", null)); }
    private String bearerToken(HttpServletRequest request) { String header = request.getHeader("Authorization"); return header != null && header.startsWith("Bearer ") ? header.substring(7) : null; }
}