package com.fja.ai.tinyrag.auth.dto;

public class AuthResponse {

    private final String message;
    private final AuthUserDto user;

    public AuthResponse(String message, AuthUserDto user) {
        this.message = message;
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public AuthUserDto getUser() {
        return user;
    }
}
