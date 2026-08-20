package com.bajar.saman.controller;

import com.bajar.saman.entity.User;
import com.bajar.saman.service.RoleManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sellers")
@PreAuthorize("hasRole('ADMIN')")
// Class-level @PreAuthorize — applies to EVERY method in this controller,
// not repeated per-endpoint. Appropriate here specifically because this
// entire controller is admin-only by nature (unlike CategoryController,
// where only mutation endpoints needed the restriction and GET stayed
// public) — no method in this class should ever be reachable by a
// non-admin.
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    public RoleManagementController(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<SellerApplicationResponse>> getPending() {
        List<SellerApplicationResponse> response = roleManagementService.getPendingSellerApplications()
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/approve")
    public ResponseEntity<SellerApplicationResponse> approve(@PathVariable UUID userId) {
        User user = roleManagementService.approveSeller(userId);
        return ResponseEntity.ok(toResponse(user));
    }

    @PatchMapping("/{userId}/reject")
    public ResponseEntity<SellerApplicationResponse> reject(@PathVariable UUID userId) {
        User user = roleManagementService.rejectSeller(userId);
        return ResponseEntity.ok(toResponse(user));
    }

    private SellerApplicationResponse toResponse(User user) {
        return new SellerApplicationResponse(user.getId(), user.getEmail(), user.getSellerStatus().name());
    }

    private record SellerApplicationResponse(UUID id, String email, String sellerStatus) {
    }
}