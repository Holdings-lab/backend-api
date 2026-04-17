package com.project.server.repository;

import com.project.server.domain.EmailVerificationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCodeEntity, Long> {
    Optional<EmailVerificationCodeEntity> findTopByEmailOrderByIdDesc(String email);
    void deleteByEmail(String email);
}
