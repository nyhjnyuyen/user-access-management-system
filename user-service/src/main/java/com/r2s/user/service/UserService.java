package com.r2s.user.service;

import com.r2s.core.dto.InternalUserInfoResponse;
import com.r2s.core.entity.Role;
import com.r2s.core.exception.CustomException;
import com.r2s.user.dto.RegisterRequest;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.core.entity.User;
import com.r2s.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository repo;
    private final RestTemplate restTemplate;
    private final AuthServiceClient authServiceClient;

    @Value("${app.auth-service.url}")
    private String authServiceUrl;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public UserService(UserRepository repo, RestTemplate restTemplate, AuthServiceClient authServiceClient) {
            this.repo = repo;
            this.restTemplate = restTemplate;
        this.authServiceClient = authServiceClient;
    }

    public List<UserResponse> getAllUsers() {
        return repo.findAll().stream().map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public UserResponse getUserByUsername(String username) {
        User user = ensureLocalUserExists(username);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public User ensureLocalUserExists(String username) {
        User existingUser = repo.findByUsername(username).orElse(null);
        if (existingUser != null) {
            return existingUser;
        }
        InternalUserInfoResponse user = authServiceClient.getUserInfo(username);

        User newUser = new User();
        newUser.setUsername(user.getUsername());
        newUser.setEmail(user.getEmail());
        newUser.setRole(user.getRole());
        newUser.setEnabled(user.isEnabled());
        newUser.setFullName("");
        newUser.setPassword("");

        try {
            return repo.save(newUser);
        } catch (DataIntegrityViolationException e) {
            return repo.findByUsername(username).orElseThrow(() -> e);
        }
    }

    public UserResponse updateUser (String username, UpdateUserRequest req){
        User user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Not found"));
        String newEmail = req.getEmail();

        if (newEmail != null && !newEmail.equals(user.getEmail())) {
            repo.findByEmail(newEmail).ifPresent(existingUser -> {
                throw new CustomException(HttpStatus.CONFLICT, "Email already exists");
            });
        }
        user.setFullName(req.getFullName());
        user.setEmail(newEmail);
        return UserResponse.fromEntity(repo.save(user));
    }

    public void deleteUser(String username) {
        User user = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Void> response = restTemplate.exchange(authServiceUrl + "/internal/auth-users/{username}",
                    HttpMethod.DELETE,
                    entity,
                    Void.class,
                    username
            );

            if(!response.getStatusCode().is2xxSuccessful()) {
                throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Auth-service delete failed: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot delete user in auth-service");
        }

        try {
            repo.delete(user);

        } catch (Exception e) {
            throw new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, "User deleted in auth-service but failed in user-service");
        }
    }

    @Transactional
    public void createUserFromAuth(RegisterRequest req){
        if (repo.findByUsername(req.getUsername()).isPresent()) {
            return;
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword());
        user.setRole(Role.ROLE_USER);
        user.setEmail(req.getEmail());
        user.setFullName("");
        user.setEnabled(false);
        try{
            repo.save(user);
        } catch (DataIntegrityViolationException e) {
            repo.findByUsername(req.getUsername()).orElseThrow(() -> e);
        }
    }

    @Transactional
    public void updateUserFromAuth(String username, boolean enabled){
        User user = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
        user.setEnabled(enabled);
        repo.save(user);
    }

}
