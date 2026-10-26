package com.waleed.naseej.repository;

import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
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

    // Admin: investors waiting for review (null status = signed up before verification existed)
    List<User> findByRoleAndInvestorStatusOrderByIdDesc(Role role, InvestorStatus investorStatus);

    List<User> findByRoleAndInvestorStatusIsNullOrderByIdDesc(Role role);

    List<User> findByRoleOrderByIdDesc(Role role);

    long countByRole(Role role);

    // Admin members list, newest first, with the neighborhood in the same query
    @EntityGraph(attributePaths = "neighborhood")
    List<User> findAllByOrderByIdDesc();
}
