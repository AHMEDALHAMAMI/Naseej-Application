package com.waleed.naseej.repository;

import com.waleed.naseej.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByRefreshTokenHash(String refreshTokenHash);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByNeighborhoodId(Integer neighborhoodId);
}