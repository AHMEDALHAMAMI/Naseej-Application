package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.UserRequest;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse create(UserRequest request) {
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(request.getPassword())
                .phone(request.getPhone())
                .authProvider(request.getAuthProvider())
                .googleId(request.getGoogleId())
                .role(request.getRole() != null ? request.getRole() : Role.PENDING)
                .neighborhood(request.getNeighborhoodId() != null
                        ? neighborhoodRepository.findById(request.getNeighborhoodId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Neighborhood not found with id: " + request.getNeighborhoodId()))
                        : null)
                .emailVerified(Boolean.TRUE.equals(request.getEmailVerified()))
                .whatsappOptIn(Boolean.TRUE.equals(request.getWhatsappOptIn()))
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .build();
        return toResponse(userRepository.save(user));
    }

    public UserResponse update(Integer id, UserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setPhone(request.getPhone());
        user.setAuthProvider(request.getAuthProvider());
        user.setGoogleId(request.getGoogleId());
        user.setRole(request.getRole());
        user.setNeighborhood(request.getNeighborhoodId() != null
                ? neighborhoodRepository.findById(request.getNeighborhoodId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Neighborhood not found with id: " + request.getNeighborhoodId()))
                : null);
        user.setEmailVerified(request.getEmailVerified());
        user.setWhatsappOptIn(request.getWhatsappOptIn());
        user.setEnabled(request.getEnabled());
        return toResponse(userRepository.save(user));
    }

    public void delete(Integer id) {
        userRepository.delete(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id)));
    }

    public UserResponse getById(Integer id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id)));
    }

    

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .authProvider(user.getAuthProvider())
                .googleId(user.getGoogleId())
                .role(user.getRole())
                .neighborhoodId(user.getNeighborhood() != null ? user.getNeighborhood().getId() : null)
                .emailVerified(user.getEmailVerified())
                .whatsappOptIn(user.getWhatsappOptIn())
                .enabled(user.getEnabled())
                .build();
    }
}
