package com.r2s.core.event;

public class UserDeletedEvent {
    private String username;
    public UserDeletedEvent(){}

    public UserDeletedEvent(String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}
