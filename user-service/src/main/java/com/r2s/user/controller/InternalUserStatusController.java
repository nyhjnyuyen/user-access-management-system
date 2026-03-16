package com.r2s.user.controller;

import com.r2s.core.dto.SyncUserStatusRequest;
import com.r2s.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
public class InternalUserStatusController {
    private final UserService userService;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public InternalUserStatusController(UserService userService) {
            this.userService = userService;
        }
        @PatchMapping("/{username}/enabled")
        public ResponseEntity<Void> updateEnabledStatus(@RequestHeader("X-Internal-Secret") String secret,
                                                        @PathVariable("username") String username, @RequestBody SyncUserStatusRequest req) {
            if (!internalSecret.equals(secret)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            userService.updateUserFromAuth(username,req.isEnabled());
            return ResponseEntity.noContent().build();
    }
}

