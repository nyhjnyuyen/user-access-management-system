package com.r2s.user.service;

import com.r2s.core.dto.InternalUserInfoResponse;
import com.r2s.core.entity.Role;
import com.r2s.core.event.UserRegisteredEvent;
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


    public UserService(UserRepository repo) {
            this.repo = repo;
    }

    public List<UserResponse> getAllUsers() {
        return repo.findAll().stream().map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public UserResponse getUserByUsername(String username) {
        User user = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
        return UserResponse.fromEntity(user);
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

    public void deleteUserProjection(String username) {
        User user = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
        repo.delete(user);
    }

    @Transactional
    public void createUserProjection(UserRegisteredEvent req){
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
    public void updateUserProjection(String username, boolean enabled){
        User user = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
        user.setEnabled(enabled);
        repo.save(user);
    }

}
