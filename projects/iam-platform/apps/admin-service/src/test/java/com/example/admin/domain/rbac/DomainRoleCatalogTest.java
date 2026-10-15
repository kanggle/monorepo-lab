package com.example.admin.domain.rbac;

import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-631 (ADR-MONO-083 D2 · D3) — {@link DomainRoleCatalog} is admin-api.md § 카탈로그's table, row for row:
 * {@code domainKey · role · access · fineGrained · label}, in the table's order. The excluded roles stay excluded.
 */
class DomainRoleCatalogTest {

    @Test
    @DisplayName("the catalog equals the contract table exactly (24 rows, order, access, fineGrained, label)")
    void catalog_equalsContractTable() {
        assertThat(DomainRoleCatalog.entries()).containsExactly(
                new DomainRoleCatalog.Entry("wms", "INVENTORY_READ", "READ", true, "재고 조회"),
                new DomainRoleCatalog.Entry("wms", "INVENTORY_WRITE", "WRITE", true, "재고 변경(조정 · 이동)"),
                new DomainRoleCatalog.Entry("wms", "INBOUND_READ", "READ", true, "입고 조회"),
                new DomainRoleCatalog.Entry("wms", "INBOUND_WRITE", "WRITE", true, "입고 변경"),
                new DomainRoleCatalog.Entry("wms", "OUTBOUND_READ", "READ", true, "출고 조회"),
                new DomainRoleCatalog.Entry("wms", "OUTBOUND_WRITE", "WRITE", true, "출고 변경"),
                new DomainRoleCatalog.Entry("wms", "MASTER_READ", "READ", true, "기준정보 조회"),
                new DomainRoleCatalog.Entry("wms", "WMS_OPERATOR", "OPERATE", true,
                        "WMS 운영 조치(알림 확인 · 운영 조치 — 업무 API 를 열지 않는다)"),
                // TASK-MONO-792 (ADR-083 P5): SCM is fine-grained; SCM_OPERATOR left the catalog (entry role —
                // derived by auth-service, never assigned).
                new DomainRoleCatalog.Entry("scm", "SCM_PROCUREMENT_READ", "READ", true, "발주 조회"),
                new DomainRoleCatalog.Entry("scm", "SCM_PROCUREMENT_WRITE", "WRITE", true,
                        "발주 변경(생성 · 제출 · 확정 · 취소 · 공급사 등록)"),
                new DomainRoleCatalog.Entry("scm", "SCM_DEMAND_PLANNING_READ", "READ", true, "수요계획 조회"),
                new DomainRoleCatalog.Entry("scm", "SCM_DEMAND_PLANNING_WRITE", "WRITE", true,
                        "수요계획 변경(승인 · 기각 · 정책 · 매핑)"),
                new DomainRoleCatalog.Entry("scm", "SCM_LOGISTICS_READ", "READ", true, "물류(배차) 조회"),
                new DomainRoleCatalog.Entry("scm", "SCM_LOGISTICS_WRITE", "WRITE", true, "물류 변경(발송 재시도)"),
                new DomainRoleCatalog.Entry("scm", "SCM_INVENTORY_VISIBILITY_READ", "READ", true, "재고 가시성 조회"),
                new DomainRoleCatalog.Entry("scm", "SCM_INVENTORY_VISIBILITY_WRITE", "WRITE", true,
                        "재고 가시성 변경(3PL 노드 등록 · 관측 재고)"),
                // TASK-MONO-793 (ADR-083 P6): ERP is fine-grained; ERP_OPERATOR left the catalog (entry role).
                new DomainRoleCatalog.Entry("erp", "ERP_MASTERDATA_READ", "READ", true, "기준정보 조회"),
                new DomainRoleCatalog.Entry("erp", "ERP_MASTERDATA_WRITE", "WRITE", true,
                        "기준정보 변경(생성 · 수정 · 퇴역 · 부서 이동 · 계정 연결 제안/철회)"),
                new DomainRoleCatalog.Entry("erp", "ERP_APPROVAL_READ", "READ", true, "결재 조회"),
                new DomainRoleCatalog.Entry("erp", "ERP_APPROVAL_WRITE", "WRITE", true,
                        "결재 변경(상신 · 승인 · 반려 · 회수 · 위임)"),
                new DomainRoleCatalog.Entry("finance", "FINANCE_OPERATOR", "OPERATE", false,
                        "Finance 전체 업무(세분 미지원)"),
                new DomainRoleCatalog.Entry("ecommerce", "ECOMMERCE_OPERATOR", "OPERATE", false,
                        "E-commerce 전체 업무(세분 미지원)"),
                new DomainRoleCatalog.Entry("fan", "FAN_OPERATOR", "OPERATE", false, "fan 전체 업무(세분 미지원)"),
                new DomainRoleCatalog.Entry("mes", "MES_OPERATOR", "OPERATE", false, "MES 전체 업무(세분 미지원)"));
    }

    @Test
    @DisplayName("administrative tiers and the explicitly excluded roles are not assignable; the pair must match")
    void excludedRoles_notAssignable() {
        for (String role : List.of("WMS_ADMIN", "MASTER_WRITE", "INVENTORY_RESERVE", "SCM_ADMIN",
                "TENANT_ADMIN", "SUPER_ADMIN")) {
            assertThat(DomainRoleCatalog.isAssignable("wms", role)).as(role).isFalse();
        }
        assertThat(DomainRoleCatalog.isAssignable("wms", "INVENTORY_READ")).isTrue();
        // the domain is part of the key: a real role under the wrong domain is not a catalog row
        assertThat(DomainRoleCatalog.isAssignable("scm", "INVENTORY_READ")).isFalse();
        assertThat(DomainRoleCatalog.isAssignable(null, "INVENTORY_READ")).isFalse();
    }

    @Test
    @DisplayName("🔴 TASK-MONO-792 Q4: SCM_OPERATOR is NOT assignable (entry role, derived by auth); the eight SCM roles are")
    void scmEntryRole_notAssignable_businessRolesAre() {
        assertThat(DomainRoleCatalog.isAssignable("scm", "SCM_OPERATOR")).isFalse();
        for (String role : List.of("SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE", "SCM_DEMAND_PLANNING_READ",
                "SCM_DEMAND_PLANNING_WRITE", "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
                "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE")) {
            assertThat(DomainRoleCatalog.isAssignable("scm", role)).as(role).isTrue();
        }
    }

    @Test
    @DisplayName("🔴 TASK-MONO-793: ERP_OPERATOR is NOT assignable (entry role, derived by auth); the four ERP roles are")
    void erpEntryRole_notAssignable_businessRolesAre() {
        assertThat(DomainRoleCatalog.isAssignable("erp", "ERP_OPERATOR")).isFalse();
        for (String role : List.of("ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
                "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE")) {
            assertThat(DomainRoleCatalog.isAssignable("erp", role)).as(role).isTrue();
        }
    }

    @Test
    @DisplayName("entitlement: identity per domain; catalog 'fan' is entitled by subscription 'fan' or 'fan-platform'")
    void isEntitled_fanMapsToBothSubscriptionKeys() {
        assertThat(DomainRoleCatalog.isEntitled("wms", Set.of("wms"))).isTrue();
        assertThat(DomainRoleCatalog.isEntitled("wms", Set.of("scm"))).isFalse();
        assertThat(DomainRoleCatalog.isEntitled("fan", Set.of("fan"))).isTrue();
        assertThat(DomainRoleCatalog.isEntitled("fan", Set.of("fan-platform"))).isTrue();
        assertThat(DomainRoleCatalog.isEntitled("fan", Set.of("wms"))).isFalse();
        assertThat(DomainRoleCatalog.isEntitled("wms", Set.of())).isFalse();
    }

    @Test
    @DisplayName("sortAndDistinct: catalog order, duplicates collapse, an unknown pair sorts last (never dropped)")
    void sortAndDistinct_catalogOrder() {
        List<RoleRef> sorted = DomainRoleCatalog.sortAndDistinct(List.of(
                new RoleRef("scm", "SCM_PROCUREMENT_READ"),
                new RoleRef("legacy", "GONE_ROLE"),
                new RoleRef("wms", "INVENTORY_WRITE"),
                new RoleRef("wms", "INVENTORY_READ"),
                new RoleRef("wms", "INVENTORY_WRITE")));
        assertThat(sorted).containsExactly(
                new RoleRef("wms", "INVENTORY_READ"),
                new RoleRef("wms", "INVENTORY_WRITE"),
                new RoleRef("scm", "SCM_PROCUREMENT_READ"),
                new RoleRef("legacy", "GONE_ROLE"));
        assertThat(DomainRoleCatalog.domainsOf(sorted)).containsExactly("legacy", "scm", "wms");
    }
}
