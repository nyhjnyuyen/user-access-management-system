package com.r2s.auth;

import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.auth.entity.ActivationToken;
import com.r2s.auth.producer.UserEventProducer;
import com.r2s.auth.repository.ActivationTokenRepository;
import com.r2s.auth.repository.UserRepository;
import com.r2s.auth.service.AuthService;
import com.r2s.auth.service.EmailService;
import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.core.event.UserActivatedEvent;
import com.r2s.core.event.UserDeletedEvent;
import com.r2s.core.event.UserRegisteredEvent;
import com.r2s.core.exception.CustomException;
import com.r2s.core.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private EmailService emailService;

    @Mock
    private ActivationTokenRepository activationTokenRepository;

    @Mock
    private UserEventProducer userEventProducer;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "activationBaseUrl", "http://localhost:8081");
    }

    private User makeUser(String username, String email, String password, Role role, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setFullName("");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    @Test
    void register_success_whenRequestValid() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setPassword("12345678");
        request.setEmail("john@example.com");

        when(userRepo.findByUsername("john")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("12345678")).thenReturn("encoded-password");
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTokenRepository.save(any(ActivationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        verify(userRepo).findByUsername("john");
        verify(userRepo).findByEmail("john@example.com");
        verify(passwordEncoder).encode("12345678");

        verify(userRepo).save(argThat(user ->
                user.getUsername().equals("john") &&
                        user.getEmail().equals("john@example.com") &&
                        user.getPassword().equals("encoded-password") &&
                        user.getRole() == Role.ROLE_USER &&
                        !user.isEnabled() &&
                        user.getCreatedAt() != null
        ));

        verify(activationTokenRepository).save(argThat(token ->
                token.getUsername().equals("john") &&
                        token.getToken() != null &&
                        token.getUsedAt() == null &&
                        token.getExpiresAt() != null
        ));

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(userEventProducer).publishedUserRegistered(eventCaptor.capture());

        UserRegisteredEvent publishedEvent = eventCaptor.getValue();
        assertEquals("john", publishedEvent.getUsername());
        assertEquals("john@example.com", publishedEvent.getEmail());
        assertEquals(Role.ROLE_USER, publishedEvent.getRole());
        assertFalse(publishedEvent.isEnabled());
        assertEquals("encoded-password", publishedEvent.getPassword());
        assertNotNull(publishedEvent.getCreatedAt());

        verify(emailService).sendActivationEmail(eq("john@example.com"), contains("/auth/activate/"));
    }

    @Test
    void register_throwsConflict_whenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setPassword("12345678");
        request.setEmail("john@example.com");

        when(userRepo.findByUsername("john"))
                .thenReturn(Optional.of(makeUser("john", "old@example.com", "pw", Role.ROLE_USER, false)));

        CustomException ex = assertThrows(CustomException.class, () -> authService.register(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Username exists", ex.getMessage());

        verify(userRepo).findByUsername("john");
        verify(userRepo, never()).findByEmail(any());
        verify(userRepo, never()).save(any());
        verify(activationTokenRepository, never()).save(any());
        verify(userEventProducer, never()).publishedUserRegistered(any());
        verify(emailService, never()).sendActivationEmail(any(), any());
    }

    @Test
    void register_throwsConflict_whenEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setPassword("12345678");
        request.setEmail("john@example.com");

        when(userRepo.findByUsername("john")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("john@example.com"))
                .thenReturn(Optional.of(makeUser("other", "john@example.com", "pw", Role.ROLE_USER, false)));

        CustomException ex = assertThrows(CustomException.class, () -> authService.register(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Email exists", ex.getMessage());

        verify(userRepo).findByUsername("john");
        verify(userRepo).findByEmail("john@example.com");
        verify(userRepo, never()).save(any());
        verify(activationTokenRepository, never()).save(any());
        verify(userEventProducer, never()).publishedUserRegistered(any());
        verify(emailService, never()).sendActivationEmail(any(), any());
    }

    @Test
    void login_success_whenCredentialsValid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("12345678");

        User user = makeUser("john", "john@example.com", "encoded-password", Role.ROLE_USER, true);

        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken("john")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());

        verify(userRepo).findByUsername("john");
        verify(passwordEncoder).matches("12345678", "encoded-password");
        verify(jwtUtil).generateToken("john");
    }

    @Test
    void login_throwsNotFound_whenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("missing");
        request.setPassword("12345678");

        when(userRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> authService.login(request));

        verify(userRepo).findByUsername("missing");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void login_throwsBadCredentials_whenPasswordIncorrect() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("wrong-password");

        User user = makeUser("john", "john@example.com", "encoded-password", Role.ROLE_USER, true);

        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        verify(userRepo).findByUsername("john");
        verify(passwordEncoder).matches("wrong-password", "encoded-password");
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void login_throwsUnprocessableEntity_whenAccountNotActivated() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("12345678");

        User user = makeUser("john", "john@example.com", "encoded-password", Role.ROLE_USER, false);

        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", "encoded-password")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class, () -> authService.login(request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("Account is not activated", ex.getMessage());

        verify(userRepo).findByUsername("john");
        verify(passwordEncoder).matches("12345678", "encoded-password");
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void activate_success_whenTokenValid() {
        User user = makeUser("john", "john@example.com", "encoded-password", Role.ROLE_USER, false);

        ActivationToken token = new ActivationToken();
        token.setToken("valid-token");
        token.setUsername("john");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUsedAt(null);

        when(activationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTokenRepository.save(any(ActivationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.activateAccount("valid-token");

        assertTrue(user.isEnabled());
        assertNotNull(token.getUsedAt());

        verify(activationTokenRepository).findByToken("valid-token");
        verify(userRepo).findByUsername("john");
        verify(userRepo).save(user);
        verify(activationTokenRepository).save(token);

        ArgumentCaptor<UserActivatedEvent> eventCaptor = ArgumentCaptor.forClass(UserActivatedEvent.class);
        verify(userEventProducer).publishedUserActivated(eventCaptor.capture());

        UserActivatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals("john", publishedEvent.getUsername());
        assertTrue(publishedEvent.isEnabled());
    }

    @Test
    void activate_throwsNotFound_whenTokenNotFound() {
        when(activationTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.activateAccount("missing-token"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Invalid token", ex.getMessage());

        verify(activationTokenRepository).findByToken("missing-token");
        verify(userRepo, never()).findByUsername(any());
        verify(userRepo, never()).save(any());
        verify(userEventProducer, never()).publishedUserActivated(any());
    }

    @Test
    void activate_throwsConflict_whenTokenAlreadyUsed() {
        ActivationToken token = new ActivationToken();
        token.setToken("used-token");
        token.setUsername("john");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUsedAt(Instant.now());

        when(activationTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.activateAccount("used-token"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Token already used", ex.getMessage());

        verify(activationTokenRepository).findByToken("used-token");
        verify(userRepo, never()).findByUsername(any());
        verify(userRepo, never()).save(any());
    }

    // ===== activate token expired =====
    @Test
    void activate_throwsConflict_whenTokenExpired() {
        ActivationToken token = new ActivationToken();
        token.setToken("expired-token");
        token.setUsername("john");
        token.setExpiresAt(Instant.now().minusSeconds(60));
        token.setUsedAt(null);

        when(activationTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.activateAccount("expired-token"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Token expired", ex.getMessage());

        verify(activationTokenRepository).findByToken("expired-token");
        verify(userRepo, never()).findByUsername(any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void activate_throwsNotFound_whenUserNotFound() {
        ActivationToken token = new ActivationToken();
        token.setToken("valid-token");
        token.setUsername("john");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUsedAt(null);

        when(activationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(userRepo.findByUsername("john")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.activateAccount("valid-token"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("User is not found", ex.getMessage());

        verify(activationTokenRepository).findByToken("valid-token");
        verify(userRepo).findByUsername("john");
        verify(userRepo, never()).save(any());
        verify(userEventProducer, never()).publishedUserActivated(any());
    }

    @Test
    void deleteUser_success_whenUserExists() {
        User user = makeUser("john", "john@example.com", "encoded-password", Role.ROLE_USER, true);

        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));

        authService.deleteUser("john");

        verify(userRepo).findByUsername("john");
        verify(userRepo).delete(user);

        ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(userEventProducer).publishedUserDeleted(eventCaptor.capture());

        assertEquals("john", eventCaptor.getValue().getUsername());
    }

    @Test
    void deleteUser_throwsNotFound_whenUserNotFound() {
        when(userRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> authService.deleteUser("missing"));

        verify(userRepo).findByUsername("missing");
        verify(userRepo, never()).delete(any());
        verify(userEventProducer, never()).publishedUserDeleted(any());
    }
}