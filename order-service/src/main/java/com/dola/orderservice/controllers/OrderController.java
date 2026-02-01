package com.dola.orderservice.controllers;

import com.dola.orderservice.entities.Order;
import com.dola.orderservice.repositories.OrderRepository;
import com.dola.orderservice.clients.UserServiceClient;
import com.dola.orderservice.events.OrderCreatedEvent;
import com.dola.orderservice.events.OrderEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @PostMapping
    public Object createOrder(@RequestBody Order order) {
        // Validate user exists by calling user-service
        boolean userExists = userServiceClient.userExists(order.getUserId());

        if (!userExists) {
            return new ErrorResponse("User not found", "Cannot create order for non-existent user");
        }

        // User exists, create the order
        Order savedOrder = orderRepository.save(order);

        System.out.println("DEBUG: Order saved with ID: " + savedOrder.getId());
        System.out.println("DEBUG: orderEventPublisher is: " + orderEventPublisher);



        // ✨ NEW: Emit OrderCreatedEvent to Kafka
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getProductName(),
                savedOrder.getProductPrice(),
                savedOrder.getTotal(),
                savedOrder.getStatus()
        );
// Then call the publisher
        if (orderEventPublisher != null) {
            System.out.println("DEBUG: About to publish event");
            orderEventPublisher.publishOrderCreatedEvent(event);
            System.out.println("DEBUG: Event published");
        } else {
            System.out.println("DEBUG: orderEventPublisher is NULL!");
        }
        return savedOrder;
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @GetMapping
    public Iterable<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // Helper class for error responses
    public static class ErrorResponse {
        public String error;
        public String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }
    }
}