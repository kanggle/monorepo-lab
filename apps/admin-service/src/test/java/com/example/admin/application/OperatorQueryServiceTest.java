package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperatorQueryServiceTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock AdminOperatorRoleJpaRepository operatorRoleRepository;
    @Mock AdminRoleJpaRepository roleRepository;
    @Mock AdminOperatorTotpJpaRepository totpRepository;

    @InjectMocks OperatorQueryService service;

    private AdminRoleJpaEntity role(Long id, String name) {
        try {
            var ctor = AdminRoleJpaEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AdminRoleJpaEntity r = ctor.newInstance();
            setField(r, "id", id);
            setField(r, "name", name);
            setField(r, "description", name);
            return r;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private AdminOperatorJpaEntity operator(Long id, String uuid, String email, String status) {
        AdminOperatorJpaEntity e = AdminOperatorJpaEntity.create(
                uuid, email, "hash", "Display", status, Instant.parse("2026-01-01T00:00:00Z"));
        setField(e, "id", id);
        return e;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    @DisplayName("현재 운영자 조회 시 role 목록이 포함된 summary 를 반환한다")
    void getCurrentOperator_returns_summary_with_roles() {
        AdminOperatorJpaEntity op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorRoleRepository.findByOperatorId(10L)).thenReturn(List.of(
                AdminOperatorRoleJpaEntity.create(10L, 1L, Instant.now(), null)));
        when(roleRepository.findAllById(anyCollection())).thenReturn(List.of(role(1L, "SUPER_ADMIN")));
        when(totpRepository.findById(10L)).thenReturn(Optional.empty());

        OperatorQueryService.OperatorSummary result = service.getCurrentOperator("op-uuid");

        assertThat(result.operatorId()).isEqualTo("op-uuid");
        assertThat(result.email()).isEqualTo("op@example.com");
        assertThat(result.roles()).containsExactly("SUPER_ADMIN");
        assertThat(result.totpEnrolled()).isFalse();
    }

    @Test
    @DisplayName("운영자 레지스트리에 없는 토큰으로 조회하면 OperatorUnauthorizedException 을 던진다")
    void getCurrentOperator_missing_operator_throws_unauthorized() {
        when(operatorRepository.findByOperatorId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentOperator("missing"))
                .isInstanceOf(OperatorUnauthorizedException.class);
    }

    @Test
    @DisplayName("운영자 목록 조회 시 페이지마다 role 매핑이 포함된다")
    void listOperators_returns_paginated_roles() {
        AdminOperatorJpaEntity op = operator(1L, "op-1-uuid", "one@ex.com", "ACTIVE");
        org.springframework.data.domain.Page<AdminOperatorJpaEntity> page =
                new org.springframework.data.domain.PageImpl<>(
                        List.of(op),
                        org.springframework.data.domain.PageRequest.of(0, 20),
                        1L);
        when(operatorRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(operatorRoleRepository.findByOperatorIdIn(anyCollection()))
                .thenReturn(List.of(AdminOperatorRoleJpaEntity.create(1L, 3L, Instant.now(), null)));
        when(roleRepository.findAllById(anyCollection()))
                .thenReturn(List.of(role(3L, "SUPPORT_LOCK")));
        when(totpRepository.findByOperatorIdIn(anyCollection())).thenReturn(List.of());

        OperatorQueryService.OperatorPage result = service.listOperators(null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).roles()).containsExactly("SUPPORT_LOCK");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("status 필터가 주어지면 findByStatus 경로로 라우팅되며 findAll 은 호출되지 않는다")
    void listOperators_status_filter_routes_to_status_query() {
        org.springframework.data.domain.Page<AdminOperatorJpaEntity> empty =
                new org.springframework.data.domain.PageImpl<>(List.of());
        when(operatorRepository.findByStatus(
                org.mockito.ArgumentMatchers.eq("SUSPENDED"),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(empty);

        OperatorQueryService.OperatorPage result = service.listOperators("SUSPENDED", 0, 20);

        assertThat(result.content()).isEmpty();
        verify_never_findAll();
    }

    private void verify_never_findAll() {
        org.mockito.Mockito.verify(operatorRepository, never())
                .findAll(any(org.springframework.data.domain.Pageable.class));
    }
}
