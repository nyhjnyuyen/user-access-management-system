package com.r2s.user;

import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.core.event.UserRegisteredEvent;
import com.r2s.core.exception.CustomException;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.user.repository.UserRepository;
import com.r2s.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repo;

    @InjectMocks
    private UserService userService;

    private User makeUser(String username, String email, String fullName, Role role, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setPassword("12345678");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    @Test
    void getAllUsers() {
        List<User> mockUsers = List.of(
                makeUser("john", "john@example.com", "", Role.ROLE_USER, false),
                makeUser("jane", "jane@example.com", "", Role.ROLE_USER, false)
        );

        when(repo.findAll()).thenReturn(mockUsers);

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(2, result.size());
        assertEquals("john", result.get(0).getUsername());
        assertEquals("jane", result.get(1).getUsername());
        verify(repo, times(1)).findAll();
    }

    @Test
    void getUserByUsername() {
        User mockUser = makeUser("john", "john@example.com", "", Role.ROLE_USER, false);

        when(repo.findByUsername("john")).thenReturn(Optional.of(mockUser));

        UserResponse result = userService.getUserByUsername("john");

        assertEquals("john", result.getUsername());
        assertEquals("john@example.com", result.getEmail());
        verify(repo, times(1)).findByUsername("john");
    }

    @Test
    void getUserByUsername_exception() {
        when(repo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.getUserByUsername("missing"));

        verify(repo, times(1)).findByUsername("missing");
    }

    @Test
    void updateUser() {
        User existingUser = makeUser("john", "old@example.com", "Old Name", Role.ROLE_USER, false);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("New Name");
        req.setEmail("new@example.com");

        when(repo.findByUsername("john")).thenReturn(Optional.of(existingUser));
        when(repo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(repo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse result = userService.updateUser("john", req);

        assertEquals("New Name", result.getFullName());
        assertEquals("new@example.com", result.getEmail());

        verify(repo, times(1)).findByUsername("john");
        verify(repo, times(1)).findByEmail("new@example.com");
        verify(repo, times(1)).save(existingUser);
    }

    @Test
    void updateUser_shouldThrowIfUserNotFound() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("New Name");
        req.setEmail("new@example.com");

        when(repo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.updateUser("missing", req));

        verify(repo, times(1)).findByUsername("missing");
        verify(repo, never()).findByEmail(any());
        verify(repo, never()).save(any());
    }

    @Test
    void updateUser_duplicateException() {
        User existingUser = makeUser("john", "old@example.com", "John", Role.ROLE_USER, false);
        User anotherUser = makeUser("jane", "new@example.com", "", Role.ROLE_USER, false);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("New Name");
        req.setEmail("new@example.com");

        when(repo.findByUsername("john")).thenReturn(Optional.of(existingUser));
        when(repo.findByEmail("new@example.com")).thenReturn(Optional.of(anotherUser));

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.updateUser("john", req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Email already exists", ex.getMessage());

        verify(repo, times(1)).findByUsername("john");
        verify(repo, times(1)).findByEmail("new@example.com");
        verify(repo, never()).save(any());
    }

    @Test
    void updateUser_unchangedEmail() {
        User existingUser = makeUser("john", "same@example.com", "Old Name", Role.ROLE_USER, false);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("New Name");
        req.setEmail("same@example.com");

        when(repo.findByUsername("john")).thenReturn(Optional.of(existingUser));
        when(repo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse result = userService.updateUser("john", req);

        assertEquals("New Name", result.getFullName());
        assertEquals("same@example.com", result.getEmail());

        verify(repo, times(1)).findByUsername("john");
        verify(repo, never()).findByEmail(any());
        verify(repo, times(1)).save(existingUser);
    }

    @Test
    void deleteUserProjection() {
        User mockUser = makeUser("john", "john@example.com", "", Role.ROLE_USER, false);

        when(repo.findByUsername("john")).thenReturn(Optional.of(mockUser));

        userService.deleteUserProjection("john");

        verify(repo, times(1)).findByUsername("john");
        verify(repo, times(1)).delete(mockUser);
    }

    @Test
    void deleteUserProjection_notFoundException() {
        when(repo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.deleteUserProjection("missing"));

        verify(repo, times(1)).findByUsername("missing");
        verify(repo, never()).delete(any());
    }

    @Test
    void createUserProjection() {
        LocalDateTime createdAt = LocalDateTime.now();

        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUsername("john");
        event.setPassword("1234");
        event.setEmail("john@example.com");
        event.setCreatedAt(createdAt);

        when(repo.findByUsername("john")).thenReturn(Optional.empty());
        when(repo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUserProjection(event);

        verify(repo, times(1)).findByUsername("john");
        verify(repo, times(1)).save(argThat(user ->
                user.getUsername().equals("john") &&
                        user.getPassword().equals("1234") &&
                        user.getEmail().equals("john@example.com") &&
                        user.getRole() == Role.ROLE_USER &&
                        user.getFullName().isEmpty() &&
                        !user.isEnabled() &&
                        createdAt.equals(user.getCreatedAt())
        ));
    }

    @Test
    void createUserProjection_duplicatedUsernameException() {
        User existingUser = makeUser("john", "john@example.com", "", Role.ROLE_USER, false);

        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUsername("john");

        when(repo.findByUsername("john")).thenReturn(Optional.of(existingUser));

        userService.createUserProjection(event);

        verify(repo, times(1)).findByUsername("john");
        verify(repo, never()).save(any());
    }

    @Test
    void createUserProjection_dataIntegrityException() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUsername("john");
        event.setPassword("1234");
        event.setEmail("john@example.com");
        event.setCreatedAt(LocalDateTime.now());

        when(repo.findByUsername("john"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(makeUser("john", "john@example.com", "", Role.ROLE_USER, false)));

        when(repo.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertDoesNotThrow(() -> userService.createUserProjection(event));

        verify(repo, times(2)).findByUsername("john");
        verify(repo, times(1)).save(any(User.class));
    }

    @Test
    void createUserProjection_rethrowException() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUsername("john");
        event.setPassword("1234");
        event.setEmail("john@example.com");
        event.setCreatedAt(LocalDateTime.now());

        when(repo.findByUsername("john"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        when(repo.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DataIntegrityViolationException.class,
                () -> userService.createUserProjection(event));

        verify(repo, times(2)).findByUsername("john");
        verify(repo, times(1)).save(any(User.class));
    }

    @Test
    void updateUserProjection_statusUpdated() {
        User user = makeUser("john", "john@example.com", "", Role.ROLE_USER, false);

        when(repo.findByUsername("john")).thenReturn(Optional.of(user));
        when(repo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateUserProjection("john", true);

        assertTrue(user.isEnabled());
        verify(repo, times(1)).findByUsername("john");
        verify(repo, times(1)).save(user);
    }

    @Test
    void updateUserProjection_notFoundException() {
        when(repo.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.updateUserProjection("missing", true));

        verify(repo, times(1)).findByUsername("missing");
        verify(repo, never()).save(any());
    }
}