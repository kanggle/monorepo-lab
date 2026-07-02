package com.example.notification.application.service;

import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.application.port.in.ManageTemplateUseCase;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.application.result.TemplateSummaryResult;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.domain.exception.TemplateAlreadyExistsException;
import com.example.notification.domain.exception.TemplateNotFoundException;
import com.example.notification.domain.model.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService implements ManageTemplateUseCase {

    private final TemplateRepository templateRepository;

    public PageResult<NotificationTemplate> getTemplates(PageQuery pageQuery) {
        return templateRepository.findAll(pageQuery);
    }

    @Override
    public TemplateSummaryResult getTemplateSummary() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime now = ZonedDateTime.now(kst);
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(kst);
        ZonedDateTime weekStart = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(kst);
        ZonedDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(kst);

        long total = templateRepository.countAll();
        long today = templateRepository.countCreatedBetween(
                todayStart.toLocalDateTime(), now.toLocalDateTime());
        long week = templateRepository.countCreatedBetween(
                weekStart.toLocalDateTime(), now.toLocalDateTime());
        long month = templateRepository.countCreatedBetween(
                monthStart.toLocalDateTime(), now.toLocalDateTime());

        return new TemplateSummaryResult(today, week, month, total);
    }

    @Override
    public NotificationTemplate getTemplate(String templateId) {
        // findById is tenant-scoped (TenantContext) — a cross-tenant or missing id resolves
        // to empty → 404 (existence hidden, cross-tenant-read-is-not-found).
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));
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
