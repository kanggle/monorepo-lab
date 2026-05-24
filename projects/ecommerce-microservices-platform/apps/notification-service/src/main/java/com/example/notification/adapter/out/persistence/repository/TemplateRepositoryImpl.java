package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.mapper.TemplatePersistenceMapper;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TemplateRepositoryImpl implements TemplateRepository {

    private final NotificationTemplateJpaRepository jpaRepository;
    private final TemplatePersistenceMapper mapper;

    @Override
    public NotificationTemplate save(NotificationTemplate template) {
        var entity = mapper.toEntity(template);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<NotificationTemplate> findById(String templateId) {
        return jpaRepository.findById(templateId).map(mapper::toDomain);
    }

    @Override
    public Optional<NotificationTemplate> findByTypeAndChannel(TemplateType type, NotificationChannel channel) {
        return jpaRepository.findByTypeAndChannel(type, channel).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTypeAndChannel(TemplateType type, NotificationChannel channel) {
        return jpaRepository.existsByTypeAndChannel(type, channel);
    }

    @Override
    public PageResult<NotificationTemplate> findAll(PageQuery pageQuery) {
        PageRequest pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<NotificationTemplate> page = jpaRepository.findAll(pageable).map(mapper::toDomain);
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
