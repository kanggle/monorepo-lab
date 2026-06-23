package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.NotificationTemplateJpaEntity;
import com.example.notification.adapter.out.persistence.mapper.TemplatePersistenceMapper;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateRepositoryImpl 단위 테스트")
class TemplateRepositoryImplTest {

    @InjectMocks
    private TemplateRepositoryImpl repositoryImpl;

    @Mock
    private NotificationTemplateJpaRepository jpaRepository;

    @Mock
    private TemplatePersistenceMapper mapper;

    @Test
    @DisplayName("PageQuery를 Pageable로 변환하여 JPA 조회 후 PageResult로 변환한다")
    void findAll_convertsPageQueryToPageableAndReturnsPageResult() {
        NotificationTemplateJpaEntity entity = mock(NotificationTemplateJpaEntity.class);
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, "Subject", "Body");

        Page<NotificationTemplateJpaEntity> jpaPage = new PageImpl<>(
                List.of(entity), PageRequest.of(0, 20), 1L);

        given(jpaRepository.findByTenantIdOrderByCreatedAtDescTemplateIdAsc(eq("ecommerce"), any(PageRequest.class))).willReturn(jpaPage);
        given(mapper.toDomain(entity)).willReturn(template);

        PageQuery pageQuery = new PageQuery(0, 20, null, null);
        PageResult<NotificationTemplate> result = repositoryImpl.findAll(pageQuery);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 결과 페이지 반환 시 PageResult 변환이 정상 동작한다")
    void findAll_emptyPage_returnsEmptyPageResult() {
        Page<NotificationTemplateJpaEntity> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0L);

        given(jpaRepository.findByTenantIdOrderByCreatedAtDescTemplateIdAsc(eq("ecommerce"), any(PageRequest.class))).willReturn(emptyPage);

        PageQuery pageQuery = new PageQuery(0, 20, null, null);
        PageResult<NotificationTemplate> result = repositoryImpl.findAll(pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
        assertThat(result.totalPages()).isEqualTo(0);
    }
}
