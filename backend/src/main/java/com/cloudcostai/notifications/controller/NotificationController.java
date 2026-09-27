package com.cloudcostai.notifications.controller;

import com.cloudcostai.auth.service.interfaces.AuthService;
import com.cloudcostai.common.response.ApiResponse;
import com.cloudcostai.notifications.dto.NotificationHistoryResponse;
import com.cloudcostai.notifications.dto.NotificationFeedResponse;
import com.cloudcostai.notifications.dto.NotificationPreferenceRequest;
import com.cloudcostai.notifications.dto.NotificationPreferenceResponse;
import com.cloudcostai.notifications.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(
            HttpServletRequest request, @RequestParam String awsAccountId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notification preferences loaded",
                notificationService.getPreferences(authenticatedUserId(request), awsAccountId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> savePreferences(
            HttpServletRequest httpRequest, @Valid @RequestBody NotificationPreferenceRequest request) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(true, "Notification preferences saved",
                notificationService.savePreferences(authenticatedUserId(httpRequest), request)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> testEmail(
            HttpServletRequest request, @RequestParam String awsAccountId) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(true,
                notificationService.sendTest(authenticatedUserId(request), awsAccountId), null));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>(false, exception.getMessage(), null));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, exception.getMessage(), null));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<NotificationHistoryResponse>>> history(
            HttpServletRequest request, @RequestParam String awsAccountId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notification history loaded",
                notificationService.history(authenticatedUserId(request), awsAccountId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationFeedResponse>> recent(
            HttpServletRequest request, @RequestParam String awsAccountId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notifications loaded",
                notificationService.recent(authenticatedUserId(request), awsAccountId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            HttpServletRequest request, @PathVariable UUID id, @RequestParam String awsAccountId) {
        try {
            notificationService.markRead(authenticatedUserId(request), awsAccountId, id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Notification marked as read", null));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, exception.getMessage(), null));
        }
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(
            HttpServletRequest request, @RequestParam String awsAccountId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notifications marked as read",
                notificationService.markAllRead(authenticatedUserId(request), awsAccountId)));
    }

    private UUID authenticatedUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        try {
            return authService.currentUser(authorization.substring(7).trim()).user().id();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
    }
}