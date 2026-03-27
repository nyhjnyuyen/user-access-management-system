package com.r2s.auth.service;

import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.core.entity.User;
import com.r2s.auth.repository.UserRepository;
import com.r2s.core.exception.CustomException;
import com.r2s.core.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AuthService {
        private final UserRepository userRepo;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtil jwtUtil;
        private final RestTemplate restTemplate;

        public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, RestTemplate restTemplate) {
                this.userRepo = userRepo;
                this.passwordEncoder = passwordEncoder;
                this.jwtUtil = jwtUtil;
                this.restTemplate = restTemplate;
        }

        public void register(RegisterRequest request) {
                if (userRepo.findByUsername(request.getUsername()).isPresent()) throw new CustomException(HttpStatus.CONFLICT, "Username exists");
                User user = new User();
                user.setUsername(request.getUsername());
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user.setRole(request.getRole());
                userRepo.save(user);

                RegisterRequest syncReq = new RegisterRequest();
                syncReq.setUsername(user.getUsername());
                syncReq.setPassword(user.getPassword()); //da hash
                syncReq.setRole(user.getRole());

                restTemplate.postForEntity(
                        "http://user-service:8082/internal/users",
                        syncReq,
                        Void.class
                );

        }

        public AuthResponse login(LoginRequest request) {
        User user = userRepo.findByUsername(request.getUsername()).orElseThrow(() -> new UsernameNotFoundException("Not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new BadCredentialsException("Invalid password");

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token);
        }
}