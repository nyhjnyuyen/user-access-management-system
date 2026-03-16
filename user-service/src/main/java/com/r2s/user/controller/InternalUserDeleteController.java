package com.r2s.user.controller;

import com.r2s.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
public class InternalUserDeleteController {

    private final UserService userService;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public InternalUserDeleteController(UserService userService) {
        this.userService = userService;
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteUser(@RequestHeader("X-Internal-Secret") String secret, @PathVariable String username) {
        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.deleteInternalUser(username);
        return ResponseEntity.noContent().build();
    }
}
