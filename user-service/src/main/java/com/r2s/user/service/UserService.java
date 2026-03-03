package com.r2s.user.service;

import com.r2s.core.exception.DeleteException;
import com.r2s.user.dto.RegisterRequest;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.core.entity.User;
import com.r2s.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        User user = repo.findByUsername(username).orElseThrow(() -> new DeleteException("User not found"));

        try{
            repo.delete(user);
        } catch (Exception e) {
            throw new DeleteException("Could not delete user");
        }
    }

    @Transactional
    public User createUserFromAuth(RegisterRequest req){
        User user = User.builder().username(req.getUsername()).password(req.getPassword()).role(req.getRole()).fullName(req.getFullName()).email(req.getEmail()).build();
        return repo.save(user);
    }
}
