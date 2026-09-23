package com.cloudcostai.auth.dto;
import com.cloudcostai.auth.model.AuthProfile;
public record AuthResponse(String token, AuthProfile user) {}