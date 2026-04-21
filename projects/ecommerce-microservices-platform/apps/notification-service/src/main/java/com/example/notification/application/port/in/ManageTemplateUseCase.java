package com.example.notification.application.port.in;

import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.domain.model.NotificationTemplate;

public interface ManageTemplateUseCase {
    PageResult<NotificationTemplate> getTemplates(PageQuery pageQuery);
    TemplateResult createTemplate(CreateTemplateCommand command);
    TemplateResult updateTemplate(UpdateTemplateCommand command);
}
