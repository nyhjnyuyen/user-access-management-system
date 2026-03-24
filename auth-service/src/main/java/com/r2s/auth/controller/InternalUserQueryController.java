package com.r2s.auth.controller;

import com.r2s.auth.repository.UserRepository;
import com.r2s.core.dto.InternalUserInfoResponse;
import com.r2s.core.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/auth-users")
public class InternalUserQueryController {

    private final UserRepository userRepo;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public InternalUserQueryController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/{username}")
    public ResponseEntity<InternalUserInfoResponse> getInternalUser(
            @RequestHeader("X-Internal-Secret") String secret, @PathVariable("username") String username
    ){
        if (!internalSecret.equals(secret)){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found in auth-service"));

        InternalUserInfoResponse response = new InternalUserInfoResponse();
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setEnabled(user.isEnabled());
        response.setEmail(user.getEmail());

        return ResponseEntity.ok(response);
    }
}
