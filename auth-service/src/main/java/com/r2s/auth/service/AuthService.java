package com.r2s.auth.service;

import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.auth.entity.ActivationToken;
import com.r2s.auth.repository.ActivationTokenRepository;
import com.r2s.core.dto.SyncUserStatusRequest;
import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.auth.repository.UserRepository;
import com.r2s.core.exception.CustomException;
import com.r2s.core.security.JwtUtil;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {
        private final UserRepository userRepo;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtil jwtUtil;
        private final RestTemplate restTemplate;
        private final EmailService emailService;
        private final ActivationTokenRepository activationTokenRepository;
        private static final Logger log = LoggerFactory.getLogger(AuthService.class);

        @Value("${app.activation.base-url}")
        private String activationBaseUrl;

        @Value("${app.user-service.url}")
        private String userServiceUrl;

        @Value("${app.internal-secret}")
        private String internalSecret;

        public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, RestTemplate restTemplate, EmailService emailService, ActivationTokenRepository activationTokenRepository) {
                this.userRepo = userRepo;
                this.passwordEncoder = passwordEncoder;
                this.jwtUtil = jwtUtil;
                this.restTemplate = restTemplate;
                this.emailService = emailService;
                this.activationTokenRepository = activationTokenRepository;
        }

        @Transactional
        public void register(RegisterRequest request) {
                log.info("Register request received for username: {}", request.getUsername());
                if (userRepo.findByUsername(request.getUsername()).isPresent()) {
                        log.warn("Register failed: user already exists, username: {}", request.getUsername());
                        throw new CustomException(HttpStatus.CONFLICT, "Username exists");
                }
                if(userRepo.findByEmail(request.getEmail()).isPresent()){
                        log.warn("Register failed: email already exists, email: {}", request.getEmail());
                        throw new CustomException(HttpStatus.CONFLICT, "Email exists");
                }
                User user = new User();
                user.setUsername(request.getUsername());
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user.setRole(Role.ROLE_USER);
                user.setEmail(request.getEmail());
                user.setEnabled(false);
                userRepo.save(user);

                RegisterRequest syncReq = new RegisterRequest();
                syncReq.setUsername(user.getUsername());
                syncReq.setPassword(user.getPassword()); //da hash
                syncReq.setEmail(user.getEmail());

                String token = java.util.UUID.randomUUID().toString();

                ActivationToken t = new ActivationToken();
                t.setToken(token);
                t.setUsername(user.getUsername());
                t.setExpiresAt(java.time.Instant.now().plusSeconds(24*3600));
                t.setUsedAt(null);
                activationTokenRepository.save(t);

                try {
                       syncUserToUserService(syncReq);
                } catch (RestClientException e){
                        log.error("Failed to sync user to user-service", e);
                        throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot sync user to user-service");
                }

                //email format
                String activationLink = activationBaseUrl + "/auth/activate/" + token;
                emailService.sendActivationEmail(user.getEmail(), activationLink);

        }

        public AuthResponse login(LoginRequest request) {
                User user = userRepo.findByUsername(request.getUsername()).orElseThrow(() -> new UsernameNotFoundException("Not found"));

                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        throw new BadCredentialsException("Invalid username or password");
                }

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
                try {
                        syncUserEnabledToUserService(user.getUsername(),true);
                } catch (RestClientException e){
                        log.error("Failed to sync enabled status to user-service for user {}", user.getUsername());
                        throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE, "Account activated in auth-service but failed to sync status in user-service");
                }
        }

        private void syncUserToUserService(RegisterRequest syncReq) {

                int maxAttempts = 3;
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Secret", internalSecret);

                HttpEntity<RegisterRequest> request = new HttpEntity<>(syncReq, headers);

                for (int i = 1; i <= maxAttempts; i++) {
                        try {
                                restTemplate.postForEntity(
                                        userServiceUrl + "/internal/users",
                                        request,
                                        Void.class
                                );
                                return;
                        } catch (RestClientException e){
                                if(i == maxAttempts) {
                                        throw e;
                                }
                                try {
                                        Thread.sleep(1000);
                                } catch (InterruptedException e1) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("Retry interrupted", e1);
                                }
                        }
                }
        }

        private void syncUserEnabledToUserService(String username, boolean enabled) {
                int maxAttempts = 3;
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internalSecret);
                SyncUserStatusRequest request = new SyncUserStatusRequest();
                request.setEnabled(enabled);
                HttpEntity<SyncUserStatusRequest> req = new HttpEntity<>(request, headers);

                for (int i = 1; i <= maxAttempts; i++) {
                        try {
                                restTemplate.exchange(
                                        userServiceUrl + "/internal/users/{username}/enabled",
                                        org.springframework.http.HttpMethod.PATCH,
                                        req,
                                        Void.class,
                                        username
                                );
                                return;
                        } catch (RestClientException e){
                                if(i == maxAttempts) {
                                        throw e;
                                }
                                try {
                                        Thread.sleep(1000);
                                } catch (InterruptedException e1) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("Retry interrupted", e1);
                                }
                        }
                }
        }
}