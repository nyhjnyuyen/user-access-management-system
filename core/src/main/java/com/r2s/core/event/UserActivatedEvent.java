package com.r2s.core.event;

public class UserActivatedEvent {
    private String username;
    private boolean enabled;
    public UserActivatedEvent() {}
    public UserActivatedEvent(String username, boolean enabled) {
        this.username = username;
        this.enabled = enabled;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
