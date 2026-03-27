package com.r2s.auth.controller;

import com.r2s.auth.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth-users")
public class InternalUserDeleteController {

    private final UserRepository userRepo;

    public InternalUserDeleteController(UserRepository userRepo) {

        this.userRepo = userRepo;
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteByUsername(@PathVariable("username") String username) {
        userRepo.deleteByUsername(username);
        return ResponseEntity.ok().build();
    }
}
