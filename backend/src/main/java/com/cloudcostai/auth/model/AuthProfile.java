package com.cloudcostai.auth.model;

import java.util.UUID;

public record AuthProfile(UUID id, String username, String email) {}