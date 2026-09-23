package com.cloudcostai.auth.service.interfaces;

import com.cloudcostai.auth.dto.AuthResponse;
import com.cloudcostai.auth.dto.LoginRequest;
import com.cloudcostai.auth.dto.RegisterRequest;

public interface AuthService {
	AuthResponse register(RegisterRequest request);
	AuthResponse login(LoginRequest request);
	AuthResponse currentUser(String token);
	void logout(String token);
}