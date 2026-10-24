package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ProposeIdeaRequest;
import com.waleed.naseej.dto.in.VoteRequest;
import com.waleed.naseej.dto.out.IdeaDetailResponse;
import com.waleed.naseej.dto.out.MapIdeaResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.DiscoverService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Ideas around the logged-in user: map, idea page, propose and vote. */
@RestController
@RequestMapping("/discover")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverService discoverService;

    @GetMapping("/map")
    public List<MapIdeaResponse> map(@AuthenticationPrincipal User user,
                                     @RequestParam(required = false) Double lat,
                                     @RequestParam(required = false) Double lng) {
        return discoverService.map(user, lat, lng);
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
}
