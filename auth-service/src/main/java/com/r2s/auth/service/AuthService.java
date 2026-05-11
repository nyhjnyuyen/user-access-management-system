package com.r2s.auth.service;

import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.auth.entity.ActivationToken;
import com.r2s.auth.repository.ActivationTokenRepository;
import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.auth.repository.UserRepository;
import com.r2s.core.event.UserActivatedEvent;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.core.event.UserRegisteredEvent;
import com.r2s.core.exception.CustomException;
import com.r2s.core.security.JwtUtil;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.r2s.auth.producer.UserEventProducer;

import java.time.LocalDateTime;

@Service
public class AuthService {
        private final UserRepository userRepo;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtil jwtUtil;
        private final EmailService emailService;
        private final ActivationTokenRepository activationTokenRepository;
        private static final Logger log = LoggerFactory.getLogger(AuthService.class);
        private final UserEventProducer userEventProducer;

        @Value("${app.activation.base-url}")
        private String activationBaseUrl;


        public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailService emailService, ActivationTokenRepository activationTokenRepository, UserEventProducer userEventProducer) {
                this.userRepo = userRepo;
                this.passwordEncoder = passwordEncoder;
                this.jwtUtil = jwtUtil;
                this.emailService = emailService;
                this.activationTokenRepository = activationTokenRepository;
                this.userEventProducer = userEventProducer;
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
                user.setCreatedAt(LocalDateTime.now());
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

                userEventProducer.publishedUserRegistered(
                        new UserRegisteredEvent(
                                user.getUsername(),
                                user.getEmail(),
                                user.getRole(),
                                user.isEnabled(),
                                user.getPassword(),
                                user.getCreatedAt()
                        )
                );

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
                        throw new CustomException(HttpStatus.UNAUTHORIZED, "Token expired");
                }

                User user = userRepo.findByUsername(t.getUsername()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "User is not found"));

                user.setEnabled(true);
                userRepo.save(user);
                t.setUsedAt(java.time.Instant.now());
                activationTokenRepository.save(t);
                userEventProducer.publishedUserActivated(
                        new UserActivatedEvent(user.getUsername(), true)
                );
        }
        @Transactional
        public void deleteUser(String username) {
                User user = userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
                userRepo.delete(user);
                userEventProducer.publishedUserDeleted(
                        new UserDeletedEvent(username)
                );
        }

}