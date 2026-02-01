package com.dola.notificationservice.events;

import com.dola.notificationservice.services.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for order events from Kafka
 * When OrderCreatedEvent is received, sends notification
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Consume OrderCreatedEvent from Kafka topic
     * This method is called automatically when a new event arrives
     */
    @KafkaListener(topics = "order-events", groupId = "notification-service-group")
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent from Kafka - Order ID: {}, User ID: {}, Product: {}",
                event.getOrderId(), event.getUserId(), event.getProductName());

        try {
            notificationService.sendOrderNotification(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getProductName()
            );
            log.info("Notification sent for Order ID: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process OrderCreatedEvent for Order ID: {} - {}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}