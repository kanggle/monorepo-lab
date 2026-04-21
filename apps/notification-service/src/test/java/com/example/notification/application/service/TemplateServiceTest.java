package com.example.notification.application.service;

import com.example.notification.application.command.CreateTemplateCommand;
import com.example.notification.application.command.UpdateTemplateCommand;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.domain.exception.TemplateAlreadyExistsException;
import com.example.notification.domain.exception.TemplateNotFoundException;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService 단위 테스트")
class TemplateServiceTest {

    @InjectMocks
    private TemplateService templateService;

    @Mock
    private TemplateRepository templateRepository;

    @Test
    @DisplayName("템플릿을 성공적으로 생성한다")
    void createTemplate_success() {
        given(templateRepository.existsByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL))
                .willReturn(false);

        NotificationTemplate saved = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, "Subject", "Body");
        given(templateRepository.save(any())).willReturn(saved);

        CreateTemplateCommand command = new CreateTemplateCommand(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, "Subject", "Body");

        TemplateResult result = templateService.createTemplate(command);

        assertThat(result.templateId()).isNotNull();
    }

    @Test
    @DisplayName("동일 type+channel 템플릿이 존재하면 예외가 발생한다")
    void createTemplate_duplicate_throws() {
        given(templateRepository.existsByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL))
                .willReturn(true);

        CreateTemplateCommand command = new CreateTemplateCommand(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, "Subject", "Body");

        assertThatThrownBy(() -> templateService.createTemplate(command))
                .isInstanceOf(TemplateAlreadyExistsException.class);
    }

    @Test
    @DisplayName("템플릿을 성공적으로 수정한다")
    void updateTemplate_success() {
        NotificationTemplate existing = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, "Old", "Old Body");
        given(templateRepository.findById(existing.getTemplateId()))
                .willReturn(Optional.of(existing));
        given(templateRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        UpdateTemplateCommand command = new UpdateTemplateCommand(
                existing.getTemplateId(), "New Subject", "New Body");

        TemplateResult result = templateService.updateTemplate(command);

        assertThat(result.templateId()).isEqualTo(existing.getTemplateId());
    }

    @Test
    @DisplayName("존재하지 않는 템플릿 수정 시 예외가 발생한다")
    void updateTemplate_notFound_throws() {
        given(templateRepository.findById("nonexistent")).willReturn(Optional.empty());

        UpdateTemplateCommand command = new UpdateTemplateCommand("nonexistent", "Subject", "Body");

        assertThatThrownBy(() -> templateService.updateTemplate(command))
                .isInstanceOf(TemplateNotFoundException.class);
    }
}
