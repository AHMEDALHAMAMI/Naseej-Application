package com.waleed.naseej.controller;

import com.waleed.naseej.dto.out.NotificationListResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The logged-in member's notifications (the bell in the header). */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public NotificationListResponse mine(@AuthenticationPrincipal User user) {
        return notificationService.mine(user);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal User user) {
        return Map.of("unread", notificationService.unread(user));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        notificationService.markRead(user, id);
        return ResponseEntity.noContent().build();
    }
}
