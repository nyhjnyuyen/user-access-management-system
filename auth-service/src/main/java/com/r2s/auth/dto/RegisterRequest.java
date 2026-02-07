package com.r2s.auth.dto;

import com.r2s.core.entity.Role;
import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    private String username;
    private String password;
    private Role role;

    @NotBlank(message = "Email is required")
    private String email;

    // Generate getters and setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

}
