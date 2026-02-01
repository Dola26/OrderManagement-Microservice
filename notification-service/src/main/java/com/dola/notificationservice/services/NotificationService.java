package com.dola.notificationservice.services;

import com.dola.notificationservice.entities.Notification;
import com.dola.notificationservice.repositories.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Send notification for order
     * Called by OrderEventListener when OrderCreatedEvent is received from Kafka
     */
    public Notification sendOrderNotification(Long orderId, Long userId, String productName) {
        String message = String.format("Order #%d created for user #%d. Product: %s", orderId, userId, productName);

        // Create notification
        Notification notification = new Notification(
                orderId,
                userId,
                message,
                "EMAIL",
                "SENT"
        );
        notification.setSentAt(LocalDateTime.now());

        // Save to database
        Notification saved = notificationRepository.save(notification);

        log.info("Notification sent - To User: {}, Type: EMAIL, Message: {}", userId, message);

        return saved;
    }

    /**
     * Retry failed notifications
     */
    public void retryFailedNotifications() {
        log.info("Retrying failed notifications...");
    }
}