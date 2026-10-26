package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ProposeIdeaRequest;
import com.waleed.naseej.dto.in.ReviseByChatRequest;
import com.waleed.naseej.dto.in.UpdateIdeaRequest;
import com.waleed.naseej.dto.in.VoteRequest;
import com.waleed.naseej.dto.out.BusinessModelResponse;
import com.waleed.naseej.dto.out.IdeaDetailResponse;
import com.waleed.naseej.dto.out.MapIdeaResponse;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.BusinessModelService;
import com.waleed.naseej.service.DiscoverService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Ideas around the logged-in user: map, idea page, propose, vote, and the founder's own actions. */
@RestController
@RequestMapping("/discover")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverService discoverService;
    private final BusinessModelService businessModelService;

    @GetMapping("/map")
    public List<MapIdeaResponse> map(@AuthenticationPrincipal User user,
                                     @RequestParam(required = false) Double lat,
                                     @RequestParam(required = false) Double lng,
                                     @RequestParam(required = false) Integer addressId) {
        return discoverService.map(user, lat, lng, addressId);
    }

    @GetMapping("/idea/{id}")
    public IdeaDetailResponse idea(@AuthenticationPrincipal User user,
                                   @PathVariable Integer id,
                                   @RequestParam(required = false) Double lat,
                                   @RequestParam(required = false) Double lng) {
        return discoverService.detail(user, id, lat, lng);
    }

    @GetMapping("/my-ideas")
    public List<IdeaDetailResponse> myIdeas(@AuthenticationPrincipal User user) {
        return discoverService.myIdeas(user);
    }

    @PostMapping("/propose")
    public ResponseEntity<IdeaDetailResponse> propose(@AuthenticationPrincipal User user,
                                                      @Valid @RequestBody ProposeIdeaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discoverService.propose(user, request));
    }

    /** Edit and recheck a draft or an idea that didn't pass. */
    @PutMapping("/draft/{id}")
    public IdeaDetailResponse updateDraft(@AuthenticationPrincipal User user,
                                          @PathVariable Integer id,
                                          @Valid @RequestBody ProposeIdeaRequest request) {
        return discoverService.updateDraft(user, id, request);
    }

    @PostMapping("/publish/{id}")
    public IdeaDetailResponse publish(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        return discoverService.publish(user, id);
    }

    @DeleteMapping("/draft/{id}")
    public ResponseEntity<Void> cancelDraft(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        discoverService.cancelDraft(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/retry-ai/{id}")
    public IdeaDetailResponse retryAi(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        return discoverService.retryAi(user, id);
    }

    @PostMapping("/vote/{id}")
    public IdeaDetailResponse vote(@AuthenticationPrincipal User user,
                                   @PathVariable Integer id,
                                   @Valid @RequestBody(required = false) VoteRequest request) {
        return discoverService.vote(user, id, request);
    }

    // ----- Resident: take a vote back, follow voted ideas -----

    @DeleteMapping("/vote/{id}")
    public IdeaDetailResponse unvote(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        return discoverService.unvote(user, id);
    }

    @GetMapping("/my-votes")
    public List<IdeaDetailResponse> myVotes(@AuthenticationPrincipal User user) {
        return discoverService.myVotes(user);
    }

    // ----- Founder: edit, withdraw, change the business model by chat -----

    @PutMapping("/idea/{id}")
    public IdeaDetailResponse updateIdea(@AuthenticationPrincipal User user,
                                         @PathVariable Integer id,
                                         @Valid @RequestBody UpdateIdeaRequest request) {
        return discoverService.updateIdea(user, id, request);
    }

    @DeleteMapping("/idea/{id}")
    public MessageResponse deleteIdea(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        return discoverService.deleteIdea(user, id);
    }

    @PutMapping("/idea/{id}/business-model/chat")
    public BusinessModelResponse reviseBusinessModel(@AuthenticationPrincipal User user,
                                                     @PathVariable Integer id,
                                                     @Valid @RequestBody ReviseByChatRequest request) {
        return businessModelService.reviseForOwner(user, id, request.getMessage());
    }
}
