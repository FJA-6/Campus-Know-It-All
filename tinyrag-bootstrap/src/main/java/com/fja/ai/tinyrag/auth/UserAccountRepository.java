package com.fja.ai.tinyrag.auth;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(UserRole role);

    long countByRole(UserRole role);

    Page<UserAccount> findByUsernameContainingIgnoreCaseOrderByUpdatedAtDesc(String username, Pageable pageable);
}
