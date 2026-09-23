package com.cloudcostai.auth.service.impl;

import com.cloudcostai.auth.dto.AuthResponse;
import com.cloudcostai.auth.dto.LoginRequest;
import com.cloudcostai.auth.dto.RegisterRequest;
import com.cloudcostai.auth.model.AppSession;
import com.cloudcostai.auth.model.AppUser;
import com.cloudcostai.auth.repository.AppSessionRepository;
import com.cloudcostai.auth.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.cloudcostai.auth.service.interfaces.AuthService;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
	private final AppUserRepository userRepository;
	private final AppSessionRepository sessionRepository;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public AuthServiceImpl(AppUserRepository userRepository, AppSessionRepository sessionRepository) {
		this.userRepository = userRepository;
		this.sessionRepository = sessionRepository;
	}

	@Override
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.existsByUsernameIgnoreCase(request.username()) || userRepository.existsByEmailIgnoreCase(request.email())) throw new IllegalArgumentException("Username or email is already registered.");
		return createSession(userRepository.save(new AppUser(request.username(), request.email(), passwordEncoder.encode(request.password()))));
	}

	@Override
	public AuthResponse login(LoginRequest request) {
		AppUser user = userRepository.findByUsernameIgnoreCase(request.identifier()).or(() -> userRepository.findByEmailIgnoreCase(request.identifier())).orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) throw new IllegalArgumentException("Invalid username or password.");
		return createSession(user);
	}

	@Override
	public AuthResponse currentUser(String token) {
		if (token == null || token.isBlank()) throw new IllegalArgumentException("Authentication is required.");
		AppSession session = sessionRepository.findById(token).filter(item -> item.getExpiresAt().isAfter(Instant.now())).orElseThrow(() -> new IllegalArgumentException("Session has expired."));
		return new AuthResponse(token, session.getUser().profile());
	}

	@Override
	public void logout(String token) { if (token != null && !token.isBlank()) sessionRepository.deleteById(token); }

	private AuthResponse createSession(AppUser user) {
		String token = UUID.randomUUID().toString().replace("-", "");
		sessionRepository.save(new AppSession(token, user, Instant.now().plusSeconds(86400)));
		return new AuthResponse(token, user.profile());
	}

}