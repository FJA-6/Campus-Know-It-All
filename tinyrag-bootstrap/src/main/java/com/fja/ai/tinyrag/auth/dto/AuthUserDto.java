package com.fja.ai.tinyrag.auth.dto;

public class AuthUserDto {

    private final Long id;
    private final String username;

    public AuthUserDto(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}
