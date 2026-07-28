package com.example.minagent.session.repository;

import com.example.minagent.session.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
