package com.example.notification.application.service;

import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
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

    @Override
    public PeriodSummary getPeriodSummary() {
        KstPeriodBounds b = KstPeriodBounds.now();

        long total = templateRepository.countAllForTenant();
        long today = templateRepository.countCreatedBetween(b.todayStartLocal(), b.nowLocal());
        long week = templateRepository.countCreatedBetween(b.weekStartLocal(), b.nowLocal());
        long month = templateRepository.countCreatedBetween(b.monthStartLocal(), b.nowLocal());

        return new PeriodSummary(today, week, month, total);
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
