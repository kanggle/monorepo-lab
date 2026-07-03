package com.example.notification.adapter.in.rest;

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

    private static final String ADMIN_ROLE = "ADMIN";

    private final ManageTemplateUseCase templateService;

    @GetMapping("/summary")
    public ResponseEntity<PeriodSummary> getTemplateSummary(
            @RequestHeader(value = "X-User-Role", required = false) String userRole
    ) {
        validateAdminRole(userRole);
        return ResponseEntity.ok(templateService.getPeriodSummary());
    }

    @GetMapping
    public ResponseEntity<TemplateListResponse> getTemplates(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAdminRole(userRole);
        PageResult<NotificationTemplate> templates = templateService.getTemplates(new PageQuery(Math.max(page, 0), size < 1 ? 20 : Math.min(size, PageQuery.MAX_SIZE), null, null));
        return ResponseEntity.ok(TemplateListResponse.from(templates));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateDetailResponse> getTemplate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String templateId
    ) {
        validateAdminRole(userRole);
        NotificationTemplate template = templateService.getTemplate(templateId);
        return ResponseEntity.ok(TemplateDetailResponse.from(template));
    }

    @PostMapping
    public ResponseEntity<TemplateIdResponse> createTemplate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody CreateTemplateRequest request
    ) {
        validateAdminRole(userRole);
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
        validateAdminRole(userRole);
        UpdateTemplateCommand command = new UpdateTemplateCommand(
                templateId, request.subject(), request.body());
        TemplateResult result = templateService.updateTemplate(command);
        return ResponseEntity.ok(TemplateIdResponse.from(result));
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AdminAccessDeniedException("Not an admin user");
        }
    }

    /**
     * The API gateway ({@code JwtHeaderEnrichmentFilter}, ADR-MONO-035 4b-2a)
     * injects {@code X-User-Role} as the COMMA-JOINED {@code roles} claim — e.g.
     * {@code "ADMIN,WMS_OPERATOR"} for a multi-domain operator. Admit when ADMIN
     * is one of the joined roles; an exact-equals check wrongly 403s any operator
     * who holds more than one role.
     */
    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if (ADMIN_ROLE.equals(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
