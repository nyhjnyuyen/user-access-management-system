package com.r2s.core.dto;

public class SyncUserStatusRequest {
    private boolean enabled;
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
