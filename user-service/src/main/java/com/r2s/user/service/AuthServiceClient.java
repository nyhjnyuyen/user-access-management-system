package com.r2s.user.service;

import com.r2s.core.dto.InternalUserInfoResponse;
import com.r2s.core.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthServiceClient {
    private final RestTemplate restTemplate;

    @Value("${app.auth-service.url}")
    private String authServiceUrl;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public AuthServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public InternalUserInfoResponse getUserInfo(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Internal-Secret", internalSecret);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<InternalUserInfoResponse> response = restTemplate.exchange(
                    authServiceUrl + "/internal/auth-users/{username}",
                    HttpMethod.GET,
                    entity,
                    InternalUserInfoResponse.class,
                    username
            );
            InternalUserInfoResponse internalUserInfoResponse = response.getBody();
            if (internalUserInfoResponse == null) {
                throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Empty response from auth-service");
            }
            return internalUserInfoResponse;
        } catch ( org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new UsernameNotFoundException("User not found in auth-service");
        } catch (RestClientException e) {
            throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot verify user with auth-service");
        }
    }
}
