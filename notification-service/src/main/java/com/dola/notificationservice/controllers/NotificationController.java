package com.dola.notificationservice.controllers;

import com.dola.notificationservice.entities.Notification;
import com.dola.notificationservice.repositories.NotificationRepository;
import com.dola.notificationservice.services.NotificationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationService notificationService,
                                  NotificationRepository notificationRepository) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    public Notification getNotification(@PathVariable Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    /**
     * Get all notifications
     */
    @GetMapping
    public Iterable<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    /**
     * Send order notification
     * In Phase 4, this will be triggered automatically by an event
     * For now, order-service can call this endpoint
     */
    @PostMapping("/order")
    public Notification sendOrderNotification(
            @RequestParam Long orderId,
            @RequestParam Long userId,
            @RequestParam String productName) {
        return notificationService.sendOrderNotification(orderId, userId, productName);
    }

    /**
     * Retry failed notifications
     */
    @PostMapping("/retry")
    public String retryFailedNotifications() {
        notificationService.retryFailedNotifications();
        return "Retry process started";
    }
}