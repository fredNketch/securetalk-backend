package com.securetalk.repository;

import com.securetalk.model.User;
import com.securetalk.model.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserKeyRepository extends JpaRepository<UserKey, Long> {
    
    Optional<UserKey> findByUser(User user);
    
    Optional<UserKey> findByUserId(Long userId);
}
