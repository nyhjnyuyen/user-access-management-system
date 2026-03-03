package com.r2s.auth;
import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.auth.repository.UserRepository;
import com.r2s.auth.service.AuthService;
import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.core.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldSaveUserAndSyncToUserService() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("john");
        req.setPassword("1234");
        req.setRole(Role.ROLE_USER);

        when(userRepo.findByUsername("john")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1234")).thenReturn("hashed1234");

        // save returns input user
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // avoid real call
        when(restTemplate.postForEntity(
                eq("http://user-service:8082/internal/users"),
                any(RegisterRequest.class),
                eq(Void.class)
        )).thenReturn(null);

        authService.register(req);

        // verify user saved with encoded password
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("john", savedUser.getUsername());
        assertEquals("hashed1234", savedUser.getPassword());
        assertEquals(Role.ROLE_USER, savedUser.getRole());

        // verify sync request
        ArgumentCaptor<RegisterRequest> syncCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(restTemplate, times(1)).postForEntity(
                eq("http://user-service:8082/internal/users"),
                syncCaptor.capture(),
                eq(Void.class)
        );

        RegisterRequest syncReq = syncCaptor.getValue();
        assertEquals("john", syncReq.getUsername());
        assertEquals("hashed1234", syncReq.getPassword()); // should be hashed
        assertEquals(Role.ROLE_USER, syncReq.getRole());
    }

    @Test
    void register_shouldThrowIfUsernameExists() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("john");
        req.setPassword("1234");
        req.setRole(Role.ROLE_USER);

        when(userRepo.findByUsername("john"))
                .thenReturn(Optional.of(User.builder().username("john").password("x").role(Role.ROLE_USER).build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(req));
        assertEquals("Username exists", ex.getMessage());

        verify(userRepo, never()).save(any());
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void login_shouldReturnToken_whenPasswordCorrect() {
        LoginRequest req = new LoginRequest();
        req.setUsername("john");
        req.setPassword("1234");

        User user = User.builder()
                .username("john")
                .password("hashed1234")
                .role(Role.ROLE_USER)
                .build();

        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "hashed1234")).thenReturn(true);
        when(jwtUtil.generateToken("john")).thenReturn("token123");

        AuthResponse res = authService.login(req);

        assertEquals("token123", res.getToken());
        verify(jwtUtil, times(1)).generateToken("john");
    }

    @Test
    void login_shouldThrowIfUserNotFound() {
        LoginRequest req = new LoginRequest();
        req.setUsername("missing");
        req.setPassword("1234");

        when(userRepo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> authService.login(req));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(anyString());
    }

    @Test
    void login_shouldThrowIfPasswordInvalid() {
        LoginRequest req = new LoginRequest();
        req.setUsername("john");
        req.setPassword("wrong");

        User user = User.builder()
                .username("john")
                .password("hashed1234")
                .role(Role.ROLE_USER)
                .build();

        when(userRepo.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed1234")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
        verify(jwtUtil, never()).generateToken(anyString());
    }
}