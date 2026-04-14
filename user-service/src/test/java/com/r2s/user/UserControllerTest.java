package com.r2s.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r2s.core.security.JwtFilter;
import com.r2s.user.controller.UserController;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(UserControllerTest.TestSecurityConfig.class)
class UserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private com.r2s.core.security.JwtUtil jwtUtil;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    // sucess getAllUSsers
    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllUsers() throws Exception {
        UserResponse user1 = new UserResponse();
        user1.setUsername("admin");
        user1.setFullName("Admin");
        user1.setEmail("admin@example.com");
        user1.setRole("ROLE_ADMIN");

        UserResponse user2 = new UserResponse();
        user2.setUsername("jane");
        user2.setFullName("Jane Smith");
        user2.setEmail("jane@example.com");
        user2.setRole("ROLE_USER");

        List<UserResponse> mockUsers = List.of(user1, user2);

        when(userService.getAllUsers()).thenReturn(mockUsers);

        mockMvc.perform(get("/users"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    //throw Forbidden for nonAdmin
    @Test
    @WithMockUser
    void getAllUsers_throwForbidden() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "john")
    void getMyProfile() throws Exception {
        UserResponse mockResponse = new UserResponse();
        mockResponse.setUsername("john");
        mockResponse.setFullName("John Doe");
        mockResponse.setEmail("john@example.com");
        mockResponse.setRole("ROLE_USER");

        when(userService.getUserByUsername("john")).thenReturn(mockResponse);

        mockMvc.perform(get("/users/me"))
                .andDo(print())
                .andExpect(status().isOk());

        verify(userService).getUserByUsername("john");
    }

    @Test
    @WithMockUser(username = "john")
    void updateMyProfile_shouldUpdateUser() throws Exception {
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setFullName("Updated Name");
        updateRequest.setEmail("updated@example.com");

        UserResponse updated = new UserResponse();
        updated.setUsername("john");
        updated.setFullName("Updated Name");
        updated.setEmail("updated@example.com");
        updated.setRole("ROLE_USER");

        when(userService.updateUser(eq("john"), any(UpdateUserRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/users/me")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(userService).updateUser(eq("john"), any(UpdateUserRequest.class));
    }
}