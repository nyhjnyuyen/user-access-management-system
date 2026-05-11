package com.r2s.auth;

import com.r2s.auth.config.RateLimitFilter;
import com.r2s.auth.config.SecurityConfig;
import com.r2s.auth.controller.RoleController;
import com.r2s.core.exception.GlobalExceptionHandler;
import com.r2s.core.security.JwtFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        })
@Import({
        RoleControllerTest.TestSecurityConfig.class,
        GlobalExceptionHandler.class
})
public class RoleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, e) -> res.sendError(401))
                            .accessDeniedHandler((req,res,e) -> res.sendError(403))
                    )
                    .authorizeHttpRequests(auth->auth
                            .requestMatchers(HttpMethod.GET, "/role/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }

    @Test
    @DisplayName("userAccess_returns200_whenRequesterHasUserRole")
    @WithMockUser(username = "john", roles = {"USER"})
    void userAccess_returns200_whenRequesterHasUserRole() throws Exception {
        mockMvc.perform(get("/role/user"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello USER"));
    }

    @Test
    @DisplayName("adminAccess_returns200_whenRequesterHasAdminRole")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminAccess_returns200_whenRequesterHasAdminRole() throws Exception {
        mockMvc.perform(get("/role/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello ADMIN"));
    }

    @Test
    @DisplayName("moderatorAccess_returns200_whenRequesterHasModeratorRole")
    @WithMockUser(username = "mod", roles = {"MODERATOR"})
    void moderatorAccess_returns200_whenRequesterHasModeratorRole() throws Exception {
        mockMvc.perform(get("/role/mod"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello MODERATOR"));
    }

    @Test
    @DisplayName("adminAccess_returns403_whenRequesterIsUser")
    @WithMockUser(username = "john", roles = {"USER"})
    void adminAccess_returns403_whenRequesterIsUser() throws Exception {
        mockMvc.perform(get("/role/admin"))
                .andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("roleEndpoint_returns401_whenRequesterNotAuthenticated")
    void roleEndpoint_returns401_whenRequesterNotAuthenticated() throws Exception {
        mockMvc.perform(get("/role/user"))
                .andExpect(status().isUnauthorized());
    }
}
