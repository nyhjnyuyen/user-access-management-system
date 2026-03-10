package com.r2s.user.service;

import com.r2s.core.entity.Role;
import com.r2s.core.exception.CustomException;
import com.r2s.user.dto.RegisterRequest;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.core.entity.User;
import com.r2s.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    @Value("${app.auth-service.url}")
    private String authServiceUrl;

    public UserService(UserRepository repo, RestTemplate restTemplate) {
        this.repo = repo;
        this.restTemplate = restTemplate;
    }
    public List<UserResponse> getAllUsers() {
        return repo.findAll().stream().map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public UserResponse getUserByUsername(String username){
        return repo.findByUsername(username)
                .map(UserResponse::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("Not found"));
    }

    public UserResponse updateUser (String username, UpdateUserRequest req){
        User user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Not found"));
        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());
        return UserResponse.fromEntity(repo.save(user));
    }

    @Transactional
    public void deleteUser(String username){
        User user = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
        try {
            restTemplate.delete(authServiceUrl +"/internal/auth-users/{username}",
                    username
            );
        } catch (RestClientException e) {
            throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot delete user in auth-service");
        }

        try {
            repo.delete(user);

        } catch (Exception e){
            throw new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, "User deleted in auth-service but failed in user-service");
        }
    }

    @Transactional
    public void createUserFromAuth(RegisterRequest req){
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword());
        user.setRole(Role.ROLE_USER);
        user.setEmail(req.getEmail());
        repo.save(user);
    }
}
