package com.dola.notificationservice.events;

import java.time.LocalDateTime;

/**
 * Event received from Kafka when an order is created
 * This is consumed by notification-service to send notifications
 */
public class OrderCreatedEvent {
    private Long orderId;
    private Long userId;
    private String productName;
    private Double productPrice;
    private Double total;
    private String status;
    private LocalDateTime createdAt;

    // Constructors
    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(Long orderId, Long userId, String productName,
                             Double productPrice, Double total, String status) {
        this.orderId = orderId;
        this.userId = userId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.total = total;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Double getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(Double productPrice) {
        this.productPrice = productPrice;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}