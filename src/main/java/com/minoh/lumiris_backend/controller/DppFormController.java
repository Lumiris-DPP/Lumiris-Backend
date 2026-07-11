package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import com.minoh.lumiris_backend.dto.out.DppVerificationResponse;
import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dpp-forms")
@RequiredArgsConstructor
public class DppFormController {

    private final DppFormService dppFormService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DppFormCreatedResponse> create(
            @RequestPart(value = "data") DppFormRequest request,
            @RequestPart(value = "productPhoto",      required = false) MultipartFile productPhoto,
            @RequestPart(value = "reachCompliance",   required = false) MultipartFile reachCompliance,
            @RequestPart(value = "euDeclaration",     required = false) MultipartFile euDeclaration,
            @RequestPart(value = "testReports",       required = false) MultipartFile testReports,
            @RequestPart(value = "transactionCerts",  required = false) MultipartFile transactionCerts,
            @RequestPart(value = "originCerts",       required = false) MultipartFile originCerts,
            @RequestPart(value = "repairManual",      required = false) MultipartFile repairManual,
            @RequestPart(value = "careGuide",         required = false) MultipartFile careGuide,
            @RequestPart(value = "endOfLifeGuide",    required = false) MultipartFile endOfLifeGuide,
            @RequestPart(value = "saleInvoice",       required = false) MultipartFile saleInvoice,
            @RequestPart(value = "creationPassport",  required = false) MultipartFile creationPassport,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        if (productPhoto     != null && !productPhoto.isEmpty())     files.put("productPhoto",     productPhoto);
        if (reachCompliance  != null && !reachCompliance.isEmpty())  files.put("reachCompliance",  reachCompliance);
        if (euDeclaration    != null && !euDeclaration.isEmpty())    files.put("euDeclaration",    euDeclaration);
        if (testReports      != null && !testReports.isEmpty())      files.put("testReports",      testReports);
        if (transactionCerts != null && !transactionCerts.isEmpty()) files.put("transactionCerts", transactionCerts);
        if (originCerts      != null && !originCerts.isEmpty())      files.put("originCerts",      originCerts);
        if (repairManual     != null && !repairManual.isEmpty())     files.put("repairManual",     repairManual);
        if (careGuide        != null && !careGuide.isEmpty())        files.put("careGuide",        careGuide);
        if (endOfLifeGuide   != null && !endOfLifeGuide.isEmpty())   files.put("endOfLifeGuide",   endOfLifeGuide);
        if (saleInvoice      != null && !saleInvoice.isEmpty())      files.put("saleInvoice",      saleInvoice);
        if (creationPassport != null && !creationPassport.isEmpty()) files.put("creationPassport", creationPassport);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dppFormService.create(request, files, principal.getUsername()));
    }

    @GetMapping
    ResponseEntity<List<DppFormSummaryResponse>> findAll(
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(dppFormService.findAllByUser(principal.getUsername()));
    }

    @GetMapping("/{id}")
    ResponseEntity<DppFormResponse> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(dppFormService.findById(id, principal.getUsername()));
    }

    @GetMapping("/{id}/verify")
    ResponseEntity<DppVerificationResponse> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(dppFormService.verify(id));
    }

    @GetMapping("/{id}/iris_score")
    ResponseEntity<IrisScoreResponse> getIrisScore(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(dppFormService.getIrisScore(id, principal.getUsername()));
    }

    @PostMapping("/compute_iris_score")
    ResponseEntity<IrisScoreResponse> computeIrisScore(@RequestBody DppScoreInput input) {
        return ResponseEntity.ok(dppFormService.computeIrisScore(input));
    }
}
