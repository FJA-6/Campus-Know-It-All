package com.fja.ai.tinyrag.auth.dto;

public class AuthUserDto {

    private final Long id;
    private final String username;
    private final String role;
    private final boolean enabled;

    public AuthUserDto(Long id, String username, String role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
