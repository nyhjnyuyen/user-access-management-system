package com.r2s.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r2s.core.entity.Role;
import com.r2s.core.entity.User;
import com.r2s.user.dto.RegisterRequest;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest()
@AutoConfigureMockMvc
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === GET /users ===
    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllUsers_shouldReturnListOfUsers() throws Exception {
        List<UserResponse> mockUsers = List.of(
                new UserResponse("admin", "Admin", "admin@example.com", Role.ROLE_ADMIN.name()),
                new UserResponse("jane", "Jane Smith", "jane@example.com", Role.ROLE_USER.name())
        );

        when(userService.getAllUsers()).thenReturn(mockUsers);

        ResultActions response = mockMvc.perform(get("/users"));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Users retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].username").value("admin"));
        verify(userService).getAllUsers();
    }

    // === POST /users === (public)
    @Test
    void createUser_shouldReturnCreatedUser() throws Exception {
        RegisterRequest request = new RegisterRequest("john", "1234", "John Doe", "john@example.com", Role.ROLE_USER);

        User createdUser = User.builder()
                .username("john")
                .password("1234")
                .fullName("John Doe")
                .email("john@example.com")
                .role(Role.ROLE_USER)
                .build();
        when(userService.createUserFromAuth(any(RegisterRequest.class))).thenReturn(createdUser);
        ResultActions response = mockMvc.perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
        response.andExpect(status().isOk()).andExpect(jsonPath("$.message").value("User created successfully")).andExpect(jsonPath("$.data.username").value("john"));

        verify(userService).createUserFromAuth(any(RegisterRequest.class));
    }

    // === GET /users/me ===
    @Test
    @WithMockUser(username = "john", roles = {"USER"})
    void getMyProfile_shouldReturnUserProfile() throws Exception {
        UserResponse mockResponse = new UserResponse("john", "John Doe", "john@example.com", Role.ROLE_USER.name());
        when(userService.getUserByUsername("john")).thenReturn(mockResponse);

        ResultActions response = mockMvc.perform(get("/users/me"));

        response
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile retrieved successfully"))
                .andExpect(jsonPath("$.data.username").value("john"));

        verify(userService).getUserByUsername("john");
    }

    // === PUT /users/me ===
    @Test

    @WithMockUser(username = "john", roles = {"USER"})
    void updateMyProfile_shouldUpdateUser() throws Exception {
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setFullName("Updated Name");
        updateRequest.setEmail("updated@example.com");
        UserResponse updated = new UserResponse("john", "Updated Name", "updated@example.com", Role.ROLE_USER.name());

        when(userService.updateUser(eq("john"), any(UpdateUserRequest.class))).thenReturn(updated);

        ResultActions response = mockMvc.perform(put("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"));

        verify(userService).updateUser(eq("john"), any(UpdateUserRequest.class));
    }

    // === DELETE /users/{username} === (ADMIN only)
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deleteUser_shouldReturnNoContent() throws Exception {
        doNothing().when(userService).deleteUser("john");
        mockMvc.perform(delete("/users/john"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser("john");
    }
}
