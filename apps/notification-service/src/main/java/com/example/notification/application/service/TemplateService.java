package com.example.notification.application.service;

import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
import com.example.notification.application.port.in.ManageTemplateUseCase;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.domain.exception.TemplateAlreadyExistsException;
import com.example.notification.domain.exception.TemplateNotFoundException;
import com.example.notification.domain.model.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService implements ManageTemplateUseCase {

    private final TemplateRepository templateRepository;

    public PageResult<NotificationTemplate> getTemplates(PageQuery pageQuery) {
        return templateRepository.findAll(pageQuery);
    }

    @Transactional
    public TemplateResult createTemplate(CreateTemplateCommand command) {
        if (templateRepository.existsByTypeAndChannel(command.type(), command.channel())) {
            throw new TemplateAlreadyExistsException(command.type().name(), command.channel().name());
        }

        NotificationTemplate template = NotificationTemplate.create(
                command.type(), command.channel(), command.subject(), command.body());

        NotificationTemplate saved = templateRepository.save(template);
        return new TemplateResult(saved.getTemplateId());
    }

    @Transactional
    public TemplateResult updateTemplate(UpdateTemplateCommand command) {
        NotificationTemplate template = templateRepository.findById(command.templateId())
                .orElseThrow(() -> new TemplateNotFoundException(command.templateId()));

        template.update(command.subject(), command.body());
        NotificationTemplate saved = templateRepository.save(template);
        return new TemplateResult(saved.getTemplateId());
    }
}
