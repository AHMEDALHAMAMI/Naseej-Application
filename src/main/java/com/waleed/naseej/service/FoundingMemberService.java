package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.FoundingMemberRequest;
import com.waleed.naseej.dto.out.FoundingMemberResponse;
import com.waleed.naseej.entity.FoundingMember;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.FoundingMemberRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FoundingMemberService {

    private final FoundingMemberRepository foundingMemberRepository;
    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;

    public List<FoundingMemberResponse> getAll() {
        return foundingMemberRepository.findAll().stream().map(this::toResponse).toList();
    }

    public FoundingMemberResponse create(FoundingMemberRequest request) {
        FoundingMember entity = FoundingMember.builder()
                .user(userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + request.getUserId())))
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .memberNumber(request.getMemberNumber())
                .ideaOwner(Boolean.TRUE.equals(request.getIdeaOwner()))
                .consentGiven(Boolean.TRUE.equals(request.getConsentGiven()))
                .build();
        return toResponse(foundingMemberRepository.save(entity));
    }

    public FoundingMemberResponse update(Integer id, FoundingMemberRequest request) {
        FoundingMember entity = foundingMemberRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Founding member not found with id: " + id));
        entity.setUser(userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId())));
        entity.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId())));
        entity.setMemberNumber(request.getMemberNumber());
        entity.setIdeaOwner(request.getIdeaOwner());
        entity.setConsentGiven(request.getConsentGiven());
        return toResponse(foundingMemberRepository.save(entity));
    }

    public void delete(Integer id) {
        foundingMemberRepository.delete(foundingMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Founding member not found with id: " + id)));
    }

    public FoundingMemberResponse getById(Integer id) {
        return toResponse(foundingMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Founding member not found with id: " + id)));
    }

    private FoundingMemberResponse toResponse(FoundingMember entity) {
        return FoundingMemberResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .ideaId(entity.getIdea().getId())
                .memberNumber(entity.getMemberNumber())
                .ideaOwner(entity.getIdeaOwner())
                .consentGiven(entity.getConsentGiven())
                .build();
    }
}
