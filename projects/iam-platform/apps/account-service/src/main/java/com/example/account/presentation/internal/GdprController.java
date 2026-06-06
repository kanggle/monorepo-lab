package com.example.account.presentation.internal;

import com.example.account.application.result.DataExportResult;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.service.DataExportUseCase;
import com.example.account.application.service.GdprDeleteUseCase;
import com.example.account.presentation.dto.request.GdprDeleteRequest;
import com.example.account.presentation.dto.response.DataExportResponse;
import com.example.account.presentation.dto.response.GdprDeleteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class GdprController {

    private final GdprDeleteUseCase gdprDeleteUseCase;
    private final DataExportUseCase dataExportUseCase;

    @PostMapping("/{accountId}/gdpr-delete")
    public ResponseEntity<GdprDeleteResponse> gdprDelete(
            @PathVariable String accountId,
            @Valid @RequestBody GdprDeleteRequest request) {

        GdprDeleteResult result = gdprDeleteUseCase.execute(accountId, request.operatorId());
        return ResponseEntity.ok(GdprDeleteResponse.from(result));
    }

    @GetMapping("/{accountId}/export")
    public ResponseEntity<DataExportResponse> export(
            @PathVariable String accountId) {

        DataExportResult result = dataExportUseCase.execute(accountId);
        return ResponseEntity.ok(DataExportResponse.from(result));
    }
}
