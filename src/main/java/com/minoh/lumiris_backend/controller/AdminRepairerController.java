package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.RejectionRequest;
import com.minoh.lumiris_backend.dto.in.RepairerImportRequest;
import com.minoh.lumiris_backend.dto.in.RepairerInviteRequest;
import com.minoh.lumiris_backend.dto.out.AdminAuditEntryResponse;
import com.minoh.lumiris_backend.dto.out.CoverageGapResponse;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.service.AdminAuditService;
import com.minoh.lumiris_backend.service.CoverageGapService;
import com.minoh.lumiris_backend.service.RepairerClaimService;
import com.minoh.lumiris_backend.service.RepairerOnboardingService;
import com.minoh.lumiris_backend.service.RepairerProspectingService;
import com.minoh.lumiris_backend.service.directory.ImportCriteria;
import com.minoh.lumiris_backend.service.directory.ImportReport;
import com.minoh.lumiris_backend.service.directory.RepairerDirectoryImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.minoh.lumiris_backend.service.AdminAuditService.TARGET_REPAIRER;

@RestController
@RequestMapping("/api/admin/repairers")
@RequiredArgsConstructor
public class AdminRepairerController {

    private final RepairerOnboardingService onboardingService;
    private final RepairerClaimService claimService;
    private final RepairerDirectoryImportService importService;
    private final RepairerProspectingService prospectingService;
    private final CoverageGapService coverageGapService;
    private final AdminAuditService auditService;

    // Zones où des consommateurs cherchent un retoucheur sans en trouver → cibler la prospection.
    @GetMapping("/coverage-gaps")
    ResponseEntity<List<CoverageGapResponse>> coverageGaps(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(coverageGapService.hotspots(days));
    }

    // Journal d'audit des actions réseau retoucheurs (import / invitation / validation / rejet).
    @GetMapping("/audit-log")
    ResponseEntity<List<AdminAuditEntryResponse>> auditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.recent(TARGET_REPAIRER, page, size));
    }

    // Déclenche un import annuaire (SIRENE…). Les fiches arrivent en UNCLAIMED, à promouvoir.
    @PostMapping("/import")
    ResponseEntity<ImportReport> importDirectory(
            @Valid @RequestBody RepairerImportRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        ImportCriteria criteria = new ImportCriteria(
                request.departments(),
                request.nafCodes(),
                request.maxPages() != null ? request.maxPages() : 0);
        ImportReport report = importService.importFrom(request.source(), criteria);
        auditService.record(principal.getUsername(), "repairer.import", TARGET_REPAIRER, null,
                request.source() + " · " + report.created() + " créée(s), " + report.updated() + " maj");
        return ResponseEntity.ok(report);
    }

    // (Re)génère un jeton de réclamation pour une fiche annuaire encore sans compte.
    @PostMapping("/{id}/claim-token")
    ResponseEntity<Map<String, UUID>> issueClaimToken(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("claimToken", claimService.issueClaimToken(id)));
    }

    // Envoie l'e-mail de prospection à un retoucheur listé (fiche sans compte).
    @PostMapping("/{id}/invite")
    ResponseEntity<Void> invite(
            @PathVariable UUID id,
            @Valid @RequestBody RepairerInviteRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        prospectingService.invite(id, request.email());
        auditService.record(principal.getUsername(), "repairer.invite", TARGET_REPAIRER, id.toString(),
                request.email());
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    ResponseEntity<List<RepairerProfileResponse>> listPending() {
        return ResponseEntity.ok(onboardingService.findPending());
    }

    @GetMapping("/all")
    ResponseEntity<List<RepairerProfileResponse>> listAll() {
        return ResponseEntity.ok(onboardingService.findAll());
    }

    @PatchMapping("/{id}/verify")
    ResponseEntity<RepairerProfileResponse> verify(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails principal
    ) {
        RepairerProfileResponse res = onboardingService.verify(id);
        auditService.record(principal.getUsername(), "repairer.verify", TARGET_REPAIRER, id.toString(),
                res.companyName());
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}/reject")
    ResponseEntity<RepairerProfileResponse> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectionRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        RejectionRequest body = request != null ? request : new RejectionRequest(null);
        RepairerProfileResponse res = onboardingService.reject(id, body);
        auditService.record(principal.getUsername(), "repairer.reject", TARGET_REPAIRER, id.toString(),
                body.reason());
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}/kyb-ongoing")
    ResponseEntity<RepairerProfileResponse> markOngoing(@PathVariable UUID id) {
        return ResponseEntity.ok(onboardingService.markKybOngoing(id));
    }

    @PatchMapping("/{id}/kyb-incomplete")
    ResponseEntity<RepairerProfileResponse> markIncomplete(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectionRequest request
    ) {
        String note = request != null ? request.reason() : null;
        return ResponseEntity.ok(onboardingService.markKybIncomplete(id, note));
    }
}
