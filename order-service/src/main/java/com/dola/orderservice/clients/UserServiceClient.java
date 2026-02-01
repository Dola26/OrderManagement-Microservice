package com.dola.orderservice.clients;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${user.service.url:http://localhost:8081}")
    private String userServiceUrl;

    /**
     * Validates if a user exists by calling user-service
     * Implements retry logic: retries 3 times with 1 second delay
     */
    public boolean userExists(Long userId) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                String url = userServiceUrl + "/users/" + userId;
                Object response = restTemplate.getForObject(url, Object.class);
                return response != null;
            } catch (RestClientException e) {
                retryCount++;
                if (retryCount < maxRetries) {
                    System.out.println("User service call failed, retrying... (" + retryCount + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(1000); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    System.out.println("User service unreachable after " + maxRetries + " retries");
                    return false;
                }
            }
        }
        return false;
    }
}