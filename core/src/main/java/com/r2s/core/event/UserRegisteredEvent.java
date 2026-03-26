package com.r2s.core.event;

import com.r2s.core.entity.Role;

public class UserRegisteredEvent {
    private String username;
    private String email;
    private Role role;
    private boolean enabled;
    private String password;

    public UserRegisteredEvent() {}
    public UserRegisteredEvent(String username, String email, Role role, boolean enabled, String password) {
        this.username = username;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.password = password;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public Role getRole() {
        return role;
    }
    public void setRole(Role role) {
        this.role = role;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public String getPassword() {return password;}
    public void setPassword(String password) {this.password = password;}


}
