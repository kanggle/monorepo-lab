package com.example.notification.application.port.in;

import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.common.summary.PeriodSummary;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.domain.model.NotificationTemplate;

public interface ManageTemplateUseCase {
    PageResult<NotificationTemplate> getTemplates(PageQuery pageQuery);

    /**
     * Returns tenant-scoped KST calendar-period-to-date counts of templates
     * (today / current ISO week / current month / total).
     */
    PeriodSummary getPeriodSummary();

    /**
     * Tenant-scoped single-template detail (TASK-BE-372 gap-fill). A cross-tenant or
     * missing {@code templateId} → 404 ({@code TemplateNotFoundException}).
     */
    NotificationTemplate getTemplate(String templateId);

    TemplateResult createTemplate(CreateTemplateCommand command);
    TemplateResult updateTemplate(UpdateTemplateCommand command);
}
