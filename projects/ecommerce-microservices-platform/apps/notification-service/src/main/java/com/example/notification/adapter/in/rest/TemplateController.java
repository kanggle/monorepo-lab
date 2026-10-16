package com.example.notification.adapter.in.rest;

import com.example.web.security.RoleListHeader;
import com.example.notification.adapter.in.rest.dto.request.CreateTemplateRequest;
import com.example.notification.adapter.in.rest.dto.request.UpdateTemplateRequest;
import com.example.notification.adapter.in.rest.dto.response.TemplateDetailResponse;
import com.example.notification.adapter.in.rest.dto.response.TemplateIdResponse;
import com.example.notification.adapter.in.rest.dto.response.TemplateListResponse;
import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.common.summary.PeriodSummary;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.application.port.in.ManageTemplateUseCase;
import com.example.notification.domain.exception.AdminAccessDeniedException;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/templates")
public class TemplateController {

    /**
     * TASK-MONO-795 (ADR-MONO-083 P8): the notification area's business roles — reads = READ ∨ WRITE, create /
     * update = WRITE (ecommerce iam-integration.md § 업무 역할). The old local check here compared the entry role
     * case-SENSITIVELY while its seven siblings ignored case; the comparison rule now lives once in
     * {@link RoleListHeader}.
     */
    static final String NOTIFICATION_READ = "ECOMMERCE_NOTIFICATION_READ";
    static final String NOTIFICATION_WRITE = "ECOMMERCE_NOTIFICATION_WRITE";

    private final ManageTemplateUseCase templateService;

    @GetMapping("/summary")
    public ResponseEntity<PeriodSummary> getTemplateSummary(
            @RequestHeader(value = "X-User-Role", required = false) String userRole
    ) {
        requireRead(userRole);
        return ResponseEntity.ok(templateService.getPeriodSummary());
    }

    @GetMapping
    public ResponseEntity<TemplateListResponse> getTemplates(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireRead(userRole);
        PageResult<NotificationTemplate> templates = templateService.getTemplates(new PageQuery(Math.max(page, 0), size < 1 ? 20 : Math.min(size, PageQuery.MAX_SIZE), null, null));
        return ResponseEntity.ok(TemplateListResponse.from(templates));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateDetailResponse> getTemplate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String templateId
    ) {
        requireRead(userRole);
        NotificationTemplate template = templateService.getTemplate(templateId);
        return ResponseEntity.ok(TemplateDetailResponse.from(template));
    }

    @PostMapping
    public ResponseEntity<TemplateIdResponse> createTemplate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody CreateTemplateRequest request
    ) {
        requireWrite(userRole);
        CreateTemplateCommand command = new CreateTemplateCommand(
                TemplateType.valueOf(request.type()),
                NotificationChannel.valueOf(request.channel()),
                request.subject(),
                request.body()
        );
        TemplateResult result = templateService.createTemplate(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(TemplateIdResponse.from(result));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateIdResponse> updateTemplate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String templateId,
            @Valid @RequestBody UpdateTemplateRequest request
    ) {
        requireWrite(userRole);
        UpdateTemplateCommand command = new UpdateTemplateCommand(
                templateId, request.subject(), request.body());
        TemplateResult result = templateService.updateTemplate(command);
        return ResponseEntity.ok(TemplateIdResponse.from(result));
    }

    private static void requireRead(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, NOTIFICATION_READ, NOTIFICATION_WRITE)) {
            throw new AdminAccessDeniedException("Not an admin user");
        }
    }

    private static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, NOTIFICATION_WRITE)) {
            throw new AdminAccessDeniedException("Not an admin user");
        }
    }
}
