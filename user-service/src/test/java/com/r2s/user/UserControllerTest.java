package com.r2s.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r2s.core.exception.GlobalExceptionHandler;
import com.r2s.core.security.JwtFilter;
import com.r2s.user.config.SecurityConfig;
import com.r2s.user.controller.UserController;
import com.r2s.user.dto.UpdateUserRequest;
import com.r2s.user.dto.UserResponse;
import com.r2s.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import({UserControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, e) -> res.sendError(401))
                            .accessDeniedHandler((req, res, e) -> res.sendError(403))
                    )
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    // sucess getAllUSsers
    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("getAllUsers_returns200_whenRequesterIsAdmin")
    void getAllUsers_returns200_whenRequesterIsAdmin() throws Exception {
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
    @WithMockUser(roles = {"USER"})
    @DisplayName("getAllUsers_returns403_whenRequesterIsNotAdmin")
    void getAllUsers_returns403_whenRequesterIsNotAdmin() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
        verify(userService, never()).getAllUsers();
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("getAllUsers_returns401_whenRequesterUnauthenticated")
    void getAllUsers_returns401_whenRequesterUnauthenticated() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getAllUsers();
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "john")
    @DisplayName("getMyProfile_returns200_whenUserExists")
    void getMyProfile_returns200_whenUserExists() throws Exception {
        UserResponse mockResponse = new UserResponse();
        mockResponse.setUsername("john");
        mockResponse.setFullName("John Doe");
        mockResponse.setEmail("john@example.com");
        mockResponse.setRole("ROLE_USER");

        when(userService.getUserByUsername("john")).thenReturn(mockResponse);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.email").value("john@example.com"));

        verify(userService).getUserByUsername("john");
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "missing")
    @DisplayName("getMyProfile_returns404_whenUserNotFound")
    void getMyProfile_returns404_whenUserNotFound() throws Exception {
        when(userService.getUserByUsername("missing"))
                .thenThrow(new UsernameNotFoundException("Not found"));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound());

        verify(userService).getUserByUsername("missing");
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("getMyProfile_returns401_whenRequesterUnauthenticated")
    void getMyProfile_returns401_whenRequesterUnauthenticated() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUserByUsername(any());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "john")
    @DisplayName("updateMyProfile_returns200_whenRequestValid")
    void updateMyProfile_returns200_whenRequestValid() throws Exception {
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        verify(userService).updateUser(eq("john"), any(UpdateUserRequest.class));
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "john")
    @DisplayName("updateMyProfile_returns400_whenRequestInvalid")
    void updateMyProfile_returns400_whenRequestInvalid() throws Exception {
        UpdateUserRequest invalidRequest = new UpdateUserRequest();
        invalidRequest.setFullName("");
        invalidRequest.setEmail("bad-email");

        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(any(), any());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("updateMyProfile_returns401_whenRequesterUnauthenticated")
    void updateMyProfile_returns401_whenRequesterUnauthenticated() throws Exception {
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setFullName("Updated Name");
        updateRequest.setEmail("updated@example.com");

        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateUser(any(), any());
        verifyNoMoreInteractions(userService);
    }

}