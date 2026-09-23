package com.cloudcostai.auth.repository;

import com.cloudcostai.auth.model.AppSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSessionRepository extends JpaRepository<AppSession, String> {}