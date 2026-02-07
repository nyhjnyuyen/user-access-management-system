package com.r2s.auth.service;

import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.auth.entity.ActivationToken;
import com.r2s.auth.repository.ActivationTokenRepository;
import com.r2s.core.entity.User;
import com.r2s.auth.repository.UserRepository;
import com.r2s.core.exception.CustomException;
import com.r2s.core.security.JwtUtil;
import jakarta.transaction.Transactional;
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
        private final EmailService emailService;
        private final ActivationTokenRepository activationTokenRepository;

        public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, RestTemplate restTemplate, EmailService emailService, ActivationTokenRepository activationTokenRepository) {
                this.userRepo = userRepo;
                this.passwordEncoder = passwordEncoder;
                this.jwtUtil = jwtUtil;
                this.restTemplate = restTemplate;
                this.emailService = emailService;
                this.activationTokenRepository = activationTokenRepository;
        }

        public void register(RegisterRequest request) {
                if (userRepo.findByUsername(request.getUsername()).isPresent()) throw new CustomException(HttpStatus.CONFLICT, "Username exists");
                if(userRepo.findByEmail(request.getEmail()).isPresent()){
                        throw new CustomException(HttpStatus.CONFLICT, "Email exists");
                }
                User user = new User();
                user.setUsername(request.getUsername());
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user.setRole(request.getRole());
                user.setEmail(request.getEmail());
                user.setEnabled(false);
                userRepo.save(user);

                String token = java.util.UUID.randomUUID().toString();

                ActivationToken t = new ActivationToken();
                t.setToken(token);
                t.setUsername(user.getUsername());
                t.setExpiresAt(java.time.Instant.now().plusSeconds(24*3600));
                t.setUsedAt(null);
                activationTokenRepository.save(t);

                String activationLink = "http://localhost:8081/auth/activate/" + token;
                emailService.sendActivationEmail(user.getEmail(), activationLink);

                RegisterRequest syncReq = new RegisterRequest();
                syncReq.setUsername(user.getUsername());
                syncReq.setPassword(user.getPassword()); //da hash
                syncReq.setRole(user.getRole());
                syncReq.setEmail(user.getEmail());


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

                if (!user.isEnabled()) throw new CustomException(HttpStatus.UNPROCESSABLE_ENTITY, "Account is not activated");

                String token = jwtUtil.generateToken(user.getUsername());
                return new AuthResponse(token);
        }

        @Transactional
        public void activateAccount(String token) {
                ActivationToken t = activationTokenRepository.findByToken(token).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Invalid token"));

                if (t.getUsedAt() != null) {
                        throw new CustomException(HttpStatus.CONFLICT, "Token already used");
                }
                if (t.getExpiresAt().isBefore(java.time.Instant.now())) {
                        throw new CustomException(HttpStatus.CONFLICT, "Token expired");
                }

                User user = userRepo.findByUsername(t.getUsername()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "User is not found"));

                user.setEnabled(true);
                userRepo.save(user);
                t.setUsedAt(java.time.Instant.now());
                activationTokenRepository.save(t);
        }
}