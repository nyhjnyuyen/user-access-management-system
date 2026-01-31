package com.r2s.user.controller;

import com.r2s.user.dto.RegisterRequest;
import com.r2s.user.repository.UserRepository;
import com.r2s.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserSyncController {

    private final UserService userService;

    public InternalUserSyncController(UserRepository repo, UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Void> createUser(@RequestBody RegisterRequest req) {
        userService.createUserFromAuth(req);
        return ResponseEntity.ok().build();
    }
}
