package com.r2s.user;

import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.core.exception.DeleteException;
import com.r2s.user.dto.RegisterRequest;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.user.repository.UserRepository;
import com.r2s.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    //Test getAllUsers()
    @Test
    void getAllUsers_shouldReturnListOfUserResponses() {
        List<User> mockUsers = List.of(
                User.builder().username("john").email("john@example.com").role(Role.ROLE_USER).build(),
                User.builder().username("jane").email("jane@example.com").role(Role.ROLE_ADMIN).build()
        );

        Mockito.when(userRepository.findAll()).thenReturn(mockUsers);

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(2, result.size());
        assertEquals("john", result.get(0).getUsername());
        assertEquals("jane", result.get(1).getUsername());

        // Verify interaction with the repository
        verify(userRepository, times(1)).findAll();
    }

    // test createUser()
    @Test
    void createUser_shouldSaveAndReturnUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setPassword("1234");

        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setRole(Role.ROLE_USER);

        User savedUser = User.builder().username("john").password("1234").fullName("John Doe").email("john@example.com").role(Role.ROLE_USER).build();

        Mockito.when(userRepository.save(Mockito.any(User.class))).thenReturn(savedUser);

        User result = userService.createUserFromAuth(request);
        assertEquals("john", result.getUsername());
        assertEquals("1234", result.getPassword()); // In real app, should check hashed

        // Verify save called with user matching request
        Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any(User.class));
    }

    // === TEST getUserByUsername() - success ===
    @Test
    void getUserByUsername_shouldReturnUserResponse() {
        User mockUser = User.builder().username("john").email("john@example.com").role(Role.ROLE_USER).build();

        Mockito.when(userRepository.findByUsername("john")).thenReturn(Optional.of(mockUser));

        UserResponse result = userService.getUserByUsername("john");

        assertEquals("john", result.getUsername());
        // Verify
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername("john");
    }

    // === TEST getUserByUsername() - not found ===
    @Test
    void getUserByUsername_shouldThrowExceptionIfNotFound() {
        Mockito.when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            userService.getUserByUsername("missing");
        });

        // Verify


        Mockito.verify(userRepository, Mockito.times(1)).findByUsername("missing");
    }

    // === TEST updateUser() ===
    @Test
    void updateUser_shouldUpdateAndReturnUserResponse() {
        User mockUser = User.builder().username("john").email("old@example.com").fullName("Old Name").role(Role.ROLE_USER).build();

        UpdateUserRequest update = new UpdateUserRequest();
        update.setFullName("New Name");
        update.setEmail("new@example.com");

        Mockito.when(userRepository.findByUsername("john")).thenReturn(Optional.of(mockUser));
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse result = userService.updateUser("john", update);

        assertEquals("New Name", result.getFullName());
        assertEquals("new@example.com", result.getEmail());

        // Verify
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername("john");
        Mockito.verify(userRepository, Mockito.times(1)).save(mockUser);
    }

    // === TEST deleteUser() - success ===
    @Test
    void deleteUser_shouldDeleteIfExists() {
        User mockUser = User.builder().username("john").build();

        Mockito.when(userRepository.findByUsername("john")).thenReturn(Optional.of(mockUser));

        userService.deleteUser("john");

        Mockito.verify(userRepository).delete(mockUser);

        // Verify
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername("john");
        Mockito.verify(userRepository, Mockito.times(1)).delete(mockUser);
    }

    // === TEST deleteUser() - user not found ===
    @Test
    void deleteUser_shouldThrowIfUserNotFound() {
        Mockito.when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(DeleteException.class, () -> userService.deleteUser("missing"));

        // Verify
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername("missing");
        Mockito.verify(userRepository, Mockito.never()).delete(Mockito.any());
    }

    // === TEST deleteUser() - exception during delete ===
    @Test
    void deleteUser_shouldThrowIfDeleteFails() {
        User mockUser = User.builder().username("john").build();

        Mockito.when(userRepository.findByUsername("john")).thenReturn(Optional.of(mockUser));
        Mockito.doThrow(new RuntimeException("DB error")).when(userRepository).delete(mockUser);

        assertThrows(DeleteException.class, () -> userService.deleteUser("john"));

        // Verify
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername("john");
        Mockito.verify(userRepository, Mockito.times(1)).delete(mockUser);
    }

}
