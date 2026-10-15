package com.example.admin.domain.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * TASK-BE-631 (ADR-MONO-083 D2 · D3) — the ONE catalog of assignable DOMAIN work roles, owned by admin-service
 * (admin-api.md § 도메인 업무 권한 세트 › 카탈로그). Entry condition: the domain's server actually checks the role.
 * Administrative tiers ({@code *_ADMIN}), {@code MASTER_WRITE}, {@code INVENTORY_RESERVE} and {@code WMS_ADMIN} are
 * NOT here — adding one needs a separate ADR.
 *
 * <p>Three readers, one source:
 * <ul>
 *   <li>the domain permission set surface (catalog read · {@code 422 DOMAIN_ROLE_NOT_ASSIGNABLE});</li>
 *   <li>{@link DelegatableRoleCatalog} — the partnership {@code delegatedScope} allow-list is derived from
 *       {@link #roleNames()} (D2: one source), its content byte-identical to the hand-kept list it replaced;</li>
 *   <li>the preset rows of V0052 ({@code <domain>-full} · {@code all-domains-full} · {@code legacy-compat})
 *       carry exactly these roles — {@code V0052MigrationShapeTest} pins the two against each other.</li>
 * </ul>
 *
 * <p>Order is the contract's table order; every list this service renders (roles of a set, effective roles,
 * {@code domainRoles}) uses it.
 *
 * <p>Framework-free — a pure domain policy, like {@link ScopeSet}.
 */
public final class DomainRoleCatalog {

    /** One catalog row — admin-api.md «Domain role wire shape». */
    public record Entry(String domainKey, String role, String access, boolean fineGrained, String label) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("wms", "INVENTORY_READ", "READ", true, "재고 조회"),
            new Entry("wms", "INVENTORY_WRITE", "WRITE", true, "재고 변경(조정 · 이동)"),
            new Entry("wms", "INBOUND_READ", "READ", true, "입고 조회"),
            new Entry("wms", "INBOUND_WRITE", "WRITE", true, "입고 변경"),
            new Entry("wms", "OUTBOUND_READ", "READ", true, "출고 조회"),
            new Entry("wms", "OUTBOUND_WRITE", "WRITE", true, "출고 변경"),
            new Entry("wms", "MASTER_READ", "READ", true, "기준정보 조회"),
            new Entry("wms", "WMS_OPERATOR", "OPERATE", true,
                    "WMS 운영 조치(알림 확인 · 운영 조치 — 업무 API 를 열지 않는다)"),
            // TASK-MONO-792 (ADR-083 P5 · owner decisions Q1–Q4) — SCM is fine-grained: the scm services check these
            // eight roles (scm iam-integration.md § 업무 역할). SCM_OPERATOR LEFT the catalog: it is the entry role,
            // derived by auth-service when ≥1 of these rides the token, never assigned (V0056 rewrote every set and
            // partnership scope that held it).
            new Entry("scm", "SCM_PROCUREMENT_READ", "READ", true, "발주 조회"),
            new Entry("scm", "SCM_PROCUREMENT_WRITE", "WRITE", true, "발주 변경(생성 · 제출 · 확정 · 취소 · 공급사 등록)"),
            new Entry("scm", "SCM_DEMAND_PLANNING_READ", "READ", true, "수요계획 조회"),
            new Entry("scm", "SCM_DEMAND_PLANNING_WRITE", "WRITE", true, "수요계획 변경(승인 · 기각 · 정책 · 매핑)"),
            new Entry("scm", "SCM_LOGISTICS_READ", "READ", true, "물류(배차) 조회"),
            new Entry("scm", "SCM_LOGISTICS_WRITE", "WRITE", true, "물류 변경(발송 재시도)"),
            new Entry("scm", "SCM_INVENTORY_VISIBILITY_READ", "READ", true, "재고 가시성 조회"),
            new Entry("scm", "SCM_INVENTORY_VISIBILITY_WRITE", "WRITE", true, "재고 가시성 변경(3PL 노드 등록 · 관측 재고)"),
            new Entry("erp", "ERP_OPERATOR", "OPERATE", false, "ERP 전체 업무(세분 미지원)"),
            new Entry("finance", "FINANCE_OPERATOR", "OPERATE", false, "Finance 전체 업무(세분 미지원)"),
            new Entry("ecommerce", "ECOMMERCE_OPERATOR", "OPERATE", false, "E-commerce 전체 업무(세분 미지원)"),
            new Entry("fan", "FAN_OPERATOR", "OPERATE", false, "fan 전체 업무(세분 미지원)"),
            new Entry("mes", "MES_OPERATOR", "OPERATE", false, "MES 전체 업무(세분 미지원)"));

    /**
     * Catalog domain → the subscription domain keys that entitle it. Identity for every domain except
     * {@code fan}, which both {@code fan} and {@code fan-platform} entitle (the same mapping the subscription
     * derivation uses today — admin-api.md § 카탈로그 note).
     */
    private static final Map<String, Set<String>> SUBSCRIPTION_KEYS = Map.of(
            "fan", Set.of("fan", "fan-platform"));

    private static final Map<String, Integer> ORDER;
    static {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < ENTRIES.size(); i++) {
            order.put(key(ENTRIES.get(i).domainKey(), ENTRIES.get(i).role()), i);
        }
        ORDER = Map.copyOf(order);
    }

    private DomainRoleCatalog() {
    }

    /** @return every catalog row, in the contract's order (immutable). */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** @return {@code true} iff {@code (domainKey, role)} is a catalog row. */
    public static boolean isAssignable(String domainKey, String role) {
        return domainKey != null && role != null && ORDER.containsKey(key(domainKey, role));
    }

    /** @return the role names of the whole catalog (immutable; insertion = catalog order). */
    public static Set<String> roleNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Entry e : ENTRIES) {
            names.add(e.role());
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    /** @return {@code true} iff the entitled subscription keys {@code entitled} cover catalog domain {@code domainKey}. */
    public static boolean isEntitled(String domainKey, Collection<String> entitled) {
        if (domainKey == null || entitled == null) {
            return false;
        }
        for (String subscriptionKey : SUBSCRIPTION_KEYS.getOrDefault(domainKey, Set.of(domainKey))) {
            if (entitled.contains(subscriptionKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sorts {@code (domainKey, role)} pairs into catalog order and removes duplicates. A pair that is not (or no
     * longer) in the catalog sorts after every catalog row, alphabetically — it is never dropped here (filtering
     * against the catalog is the issuer's job — auth-to-admin.md rule 7).
     */
    public static List<RoleRef> sortAndDistinct(Collection<RoleRef> refs) {
        Set<RoleRef> distinct = new LinkedHashSet<>(refs);
        List<RoleRef> sorted = new ArrayList<>(distinct);
        sorted.sort(Comparator
                .comparingInt((RoleRef r) -> ORDER.getOrDefault(key(r.domainKey(), r.role()), Integer.MAX_VALUE))
                .thenComparing(RoleRef::domainKey)
                .thenComparing(RoleRef::role));
        return List.copyOf(sorted);
    }

    /** @return the distinct {@code domainKey}s of {@code refs}, ascending (the wire {@code domains} field). */
    public static List<String> domainsOf(Collection<RoleRef> refs) {
        Set<String> domains = new TreeSet<>();
        for (RoleRef r : refs) {
            domains.add(r.domainKey());
        }
        return List.copyOf(domains);
    }

    /** A {@code {domainKey, role}} pair — a role of a set. */
    public record RoleRef(String domainKey, String role) {}

    private static String key(String domainKey, String role) {
        return domainKey + "\u0000" + role;
    }
}
