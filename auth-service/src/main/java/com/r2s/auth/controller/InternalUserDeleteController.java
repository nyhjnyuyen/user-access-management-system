package com.r2s.auth.controller;

import com.r2s.auth.repository.UserRepository;
import com.r2s.core.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/auth-users")
public class InternalUserDeleteController {

    private final UserRepository userRepo;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public InternalUserDeleteController(UserRepository userRepo) {

        this.userRepo = userRepo;
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteByUsername(@RequestHeader("X-Internal-Secret") String secret,
                                                 @PathVariable("username") String username) {
        if (!internalSecret.equals(secret)){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found in auth-service"));
        userRepo.delete(user);
        return ResponseEntity.noContent().build();
    }
}