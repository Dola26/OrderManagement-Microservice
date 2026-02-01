package com.dola.orderservice.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public static final String TOPIC_NAME = "order-events";

    public OrderEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            log.info("Publishing OrderCreatedEvent to Kafka - Order ID: {}, User ID: {}, Product: {}",
                    event.getOrderId(), event.getUserId(), event.getProductName());

            kafkaTemplate.send(TOPIC_NAME, String.valueOf(event.getOrderId()), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish OrderCreatedEvent for Order ID: {} - {}",
                                    event.getOrderId(), ex.getMessage());
                        } else {
                            log.info("Event published to topic: {} for Order ID: {}",
                                    TOPIC_NAME, event.getOrderId());
                        }
                    });
        } catch (Exception e) {
            log.error("Error sending OrderCreatedEvent for Order ID: {} - {}",
                    event.getOrderId(), e.getMessage());
        }
    }
}