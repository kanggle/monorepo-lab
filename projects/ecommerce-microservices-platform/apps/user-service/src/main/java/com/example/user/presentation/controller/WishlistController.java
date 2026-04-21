package com.example.user.presentation.controller;

import com.example.user.application.command.AddWishlistItemCommand;
import com.example.user.application.result.AddWishlistItemResult;
import com.example.user.application.result.WishlistCheckResult;
import com.example.user.application.result.WishlistPageResult;
import com.example.user.application.service.WishlistService;
import com.example.user.presentation.dto.request.AddWishlistItemRequest;
import com.example.user.presentation.dto.response.AddWishlistItemResponse;
import com.example.user.presentation.dto.response.WishlistCheckResponse;
import com.example.user.presentation.dto.response.WishlistPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping
    public ResponseEntity<AddWishlistItemResponse> addItem(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddWishlistItemRequest request) {
        var command = new AddWishlistItemCommand(userId, request.productId());
        AddWishlistItemResult result = wishlistService.addItem(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddWishlistItemResponse.from(result));
    }

    @GetMapping("/me")
    public ResponseEntity<WishlistPageResponse> getWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        WishlistPageResult result = wishlistService.getWishlist(userId, page, size);
        return ResponseEntity.ok(WishlistPageResponse.from(result));
    }

    @DeleteMapping("/{wishlistItemId}")
    public ResponseEntity<Void> removeItem(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID wishlistItemId) {
        wishlistService.removeItem(userId, wishlistItemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/check")
    public ResponseEntity<WishlistCheckResponse> checkItem(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam UUID productId) {
        WishlistCheckResult result = wishlistService.checkItem(userId, productId);
        return ResponseEntity.ok(WishlistCheckResponse.from(result));
    }
}
