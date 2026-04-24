package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.request.CreateTemplateRequest;
import com.example.notification.adapter.in.rest.dto.request.UpdateTemplateRequest;
import com.example.notification.adapter.in.rest.dto.response.TemplateIdResponse;
import com.example.notification.adapter.in.rest.dto.response.TemplateListResponse;
import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
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

    @GetMapping
    public ResponseEntity<TemplateListResponse> getTemplates(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAdminRole(userRole);
        PageResult<NotificationTemplate> templates = templateService.getTemplates(PageQuery.of(page, size));
        return ResponseEntity.ok(TemplateListResponse.from(templates));
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
        if (!ADMIN_ROLE.equals(userRole)) {
            throw new AdminAccessDeniedException("Not an admin user");
        }
    }
}
