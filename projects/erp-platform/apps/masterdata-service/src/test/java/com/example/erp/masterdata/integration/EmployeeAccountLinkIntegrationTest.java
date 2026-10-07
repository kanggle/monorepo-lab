package com.example.erp.masterdata.integration;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.CreateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.CreateJobGradeCommand;
import com.example.erp.masterdata.application.command.Commands.ProposeAccountLinkCommand;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employee ↔ IAM account link end-to-end on real MySQL (H2 forbidden) — TASK-ERP-BE-044.
 * Real {@code SecurityFilterChain} + {@code RoleScopeAuthorizationAdapter}, RS256 tokens signed
 * against the stubbed JWKS: each «person» is a distinct JWT {@code sub}, which is exactly what
 * the two-person rule and the addressee rule key on.
 *
 * <p>Fixtures (department / cost center / job grade / employee) are created through the
 * application service with a platform-scope actor; every behaviour under test goes through HTTP.
 * Each test uses its own random suffix, because the MySQL container is shared JVM-wide.
 */
@AutoConfigureMockMvc
class EmployeeAccountLinkIntegrationTest extends AbstractMasterdataIntegrationTest {

    private static final String BASE = "/api/erp/masterdata";
    private static final ActorContext FIXTURE_ACTOR = new ActorContext("fixture", TENANT_ERP,
            Set.of("erp.write", "erp.read"), Set.of("*"));
    private static RSAKey rsaKey;

    @Autowired MockMvc mockMvc;
    @Autowired MasterdataApplicationService service;
    @Autowired ObjectMapper objectMapper;

    private String sfx;
    private String deptId;
    private String ccId;
    private String jgId;

    @BeforeAll
    static void publishSigningKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-erp-be044").generate();
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");
    }

    @BeforeEach
    void fixtures() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        LocalDate from = LocalDate.of(2026, 1, 1);
        deptId = service.createDepartment(new CreateDepartmentCommand(FIXTURE_ACTOR,
                "BE044-D-" + sfx, "Dept " + sfx, null, from)).id();
        ccId = service.createCostCenter(new CreateCostCenterCommand(FIXTURE_ACTOR,
                "BE044-CC-" + sfx, "CC " + sfx, deptId, from)).id();
        jgId = service.createJobGrade(new CreateJobGradeCommand(FIXTURE_ACTOR,
                "BE044-JG-" + sfx, "Grade " + sfx, 10, from)).id();
    }

    private String newEmployee(String label) {
        return service.createEmployee(new CreateEmployeeCommand(FIXTURE_ACTOR,
                "BE044-" + label + "-" + sfx, label, deptId, ccId, jgId, LocalDate.of(2026, 1, 1))).id();
    }

    /** A signed erp token for {@code sub}. {@code orgScope == null} → no org_scope claim. */
    private String token(String sub, String scope, List<String> orgScope) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer("http://test-issuer")
                .claim("tenant_id", "erp")
                .claim("scope", scope)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        if (orgScope != null) claims.claim("org_scope", orgScope);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return "Bearer " + jwt.serialize();
    }

    /** HR operator: erp.write with whole-tenant org scope. */
    private String hr(String sub) throws Exception {
        return token(sub, "erp.write erp.read", List.of("*"));
    }

    /** An account owner: an erp participant (erp.read), no org scope. */
    private String owner(String sub) throws Exception {
        return token(sub, "erp.read", null);
    }

    private ResultActions propose(String bearer, String employeeId, String accountId) throws Exception {
        return mockMvc.perform(post(BASE + "/employees/" + employeeId + "/account-link-proposals")
                .header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + accountId + "\",\"reason\":\"onboarding\"}"));
    }

    private ResultActions decide(String bearer, String proposalId, String verb, String body) throws Exception {
        return mockMvc.perform(post(BASE + "/account-link-proposals/" + proposalId + "/" + verb)
                .header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String proposalId(ResultActions created) throws Exception {
        JsonNode n = objectMapper.readTree(created.andReturn().getResponse().getContentAsString());
        return n.path("data").path("id").asText();
    }

    private String dbAccountId(String employeeId) {
        return jdbcTemplate.queryForObject("SELECT account_id FROM employees WHERE id = ?", String.class, employeeId);
    }

    private int auditRows(String aggregateId, String action) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aggregate_id = ? AND action = ?",
                Integer.class, aggregateId, action);
        return n == null ? 0 : n;
    }

    private int employeeEvents(String employeeId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM masterdata_outbox WHERE aggregate_type = 'employee' AND aggregate_id = ?",
                Integer.class, employeeId);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1 + AC-7: propose → accept writes account_id; GET /employees/{id} and /me agree; audit + exactly one employee.changed")
    void proposeThenAccept() throws Exception {
        String emp = newEmployee("A1");
        String acct = "acct-" + sfx;
        int eventsBefore = employeeEvents(emp);

        mockMvc.perform(get(BASE + "/employees/" + emp).header("Authorization", hr("hr-" + sfx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").doesNotExist());
        mockMvc.perform(get(BASE + "/employees/me").header("Authorization", owner(acct)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MASTERDATA_NOT_FOUND"));

        ResultActions created = propose(hr("hr-" + sfx), emp, acct)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.proposedBy").value("hr-" + sfx));
        String pid = proposalId(created);
        assertThat(dbAccountId(emp)).as("a proposal does not write account_id").isNull();
        assertThat(employeeEvents(emp)).as("a proposal does not change the employee").isEqualTo(eventsBefore);

        mockMvc.perform(get(BASE + "/account-link-proposals/mine").header("Authorization", owner(acct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(pid))
                .andExpect(jsonPath("$.data[0].employeeName").value("A1"));

        decide(owner(acct), pid, "accept", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(emp))
                .andExpect(jsonPath("$.data.accountId").value(acct));

        assertThat(dbAccountId(emp)).isEqualTo(acct);
        mockMvc.perform(get(BASE + "/employees/" + emp).header("Authorization", hr("hr-" + sfx)))
                .andExpect(jsonPath("$.data.accountId").value(acct));
        mockMvc.perform(get(BASE + "/employees/me").header("Authorization", owner(acct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(emp))
                .andExpect(jsonPath("$.data.accountId").value(acct));

        assertThat(auditRows(pid, "PROPOSE_ACCOUNT_LINK")).isEqualTo(1);
        assertThat(auditRows(emp, "ACCEPT_ACCOUNT_LINK")).isEqualTo(1);
        assertThat(employeeEvents(emp)).isEqualTo(eventsBefore + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM employee_account_link_proposals WHERE id = ?", String.class, pid))
                .isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("🔴 AC-2: a self-addressed PENDING row inserted straight into the table (bypassing the proposal check) cannot be accepted by its proposer")
    void twoPersonRuleOnAcceptAgainstADirectRow() throws Exception {
        String emp = newEmployee("A2");
        String self = "hr-self-" + sfx;
        String pid = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO employee_account_link_proposals "
                        + "(id, tenant_id, employee_id, account_id, status, proposed_by, proposed_at, version) "
                        + "VALUES (?, 'erp', ?, ?, 'PENDING', ?, NOW(6), 0)",
                pid, emp, self, self);

        decide(owner(self), pid, "accept", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_SELF_ACCEPT"));

        assertThat(dbAccountId(emp)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM employee_account_link_proposals WHERE id = ?", String.class, pid))
                .isEqualTo("PENDING");

        // And the early proposal-side check: proposing one's own account is refused up front.
        propose(hr(self), newEmployee("A2b"), self)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_SELF_ACCEPT"));
    }

    @Test
    @DisplayName("AC-3: accept / decline by someone other than the proposed account → 403 NOT_ADDRESSEE")
    void notAddressee() throws Exception {
        String emp = newEmployee("A3");
        String pid = proposalId(propose(hr("hr-" + sfx), emp, "acct-" + sfx).andExpect(status().isCreated()));

        decide(owner("stranger-" + sfx), pid, "accept", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_NOT_ADDRESSEE"));
        decide(owner("stranger-" + sfx), pid, "decline", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_NOT_ADDRESSEE"));
        assertThat(dbAccountId(emp)).isNull();
    }

    @Test
    @DisplayName("AC-4: one account / one employee per tenant; one PENDING per employee — 409 with details.cause")
    void uniquenessConflicts() throws Exception {
        String emp1 = newEmployee("A4a");
        String emp2 = newEmployee("A4b");
        String acct = "acct-" + sfx;

        String pid = proposalId(propose(hr("hr-" + sfx), emp1, acct).andExpect(status().isCreated()));
        propose(hr("hr-" + sfx), emp1, "acct-other-" + sfx)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.cause").value("proposal_pending"));

        // Two employees may each have a PENDING proposal for the same account; the second accept loses.
        String pid2 = proposalId(propose(hr("hr-" + sfx), emp2, acct).andExpect(status().isCreated()));
        decide(owner(acct), pid, "accept", "{}").andExpect(status().isOk());
        decide(owner(acct), pid2, "accept", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_CONFLICT"))
                .andExpect(jsonPath("$.details.cause").value("account_already_linked"));
        assertThat(dbAccountId(emp2)).isNull();

        // Now linked: a fresh proposal naming the same account for another employee → 409 at proposal.
        propose(hr("hr-" + sfx), newEmployee("A4c"), acct)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.cause").value("account_already_linked"));
        propose(hr("hr-" + sfx), emp1, "acct-other-" + sfx)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.cause").value("employee_already_linked"));

        // The DB itself refuses a second employee on the same account (not only the application).
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE employees SET account_id = ? WHERE id = ?", acct, emp2))
                .isInstanceOf(DataIntegrityViolationException.class);
        // …while any number of unlinked employees coexist (NULLs are distinct under UNIQUE).
        Integer unlinked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE tenant_id = 'erp' AND account_id IS NULL", Integer.class);
        assertThat(unlinked).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("AC-4 🔴 DB unique: a second PENDING row for one employee is refused by the generated-column index; decided rows are not")
    void onePendingPerEmployeeIsADatabaseRule() {
        String emp = newEmployee("A4d");
        String insert = "INSERT INTO employee_account_link_proposals "
                + "(id, tenant_id, employee_id, account_id, status, proposed_by, proposed_at, version) "
                + "VALUES (?, 'erp', ?, ?, ?, 'hr-x', NOW(6), 0)";
        jdbcTemplate.update(insert, UUID.randomUUID().toString(), emp, "a1-" + sfx, "PENDING");
        jdbcTemplate.update(insert, UUID.randomUUID().toString(), emp, "a2-" + sfx, "REVOKED");
        jdbcTemplate.update(insert, UUID.randomUUID().toString(), emp, "a3-" + sfx, "DECLINED");

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID().toString(), emp,
                "a4-" + sfx, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AC-4 concurrency: 8 simultaneous proposals for one employee → exactly one PENDING row, the rest 409 proposal_pending (never 500)")
    void concurrentProposals() throws Exception {
        String emp = newEmployee("A4e");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String acct = "conc-" + i + "-" + sfx;
            ActorContext hrActor = new ActorContext("hr-" + sfx, TENANT_ERP, Set.of("erp.write"), Set.of("*"));
            tasks.add(() -> {
                start.await();
                return service.proposeAccountLink(new ProposeAccountLinkCommand(hrActor, emp, acct, null)).id();
            });
        }
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> t : tasks) futures.add(pool.submit(t));
        start.countDown();

        int ok = 0;
        int pendingConflicts = 0;
        for (Future<String> f : futures) {
            try {
                f.get();
                ok++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfSatisfying(EmployeeLinkConflictException.class,
                        c -> assertThat(c.conflictCause()).isEqualTo("proposal_pending"));
                pendingConflicts++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(pendingConflicts).isEqualTo(threads - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee_account_link_proposals WHERE employee_id = ? AND status = 'PENDING'",
                Integer.class, emp)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-5: proposing for a RETIRED employee → 422 employee_not_active; a linked employee who retires keeps account_id")
    void retiredEmployee() throws Exception {
        String retired = newEmployee("A5a");
        mockMvc.perform(post(BASE + "/employees/" + retired + "/retire")
                        .header("Authorization", hr("hr-" + sfx))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"left\"}"))
                .andExpect(status().isOk());
        propose(hr("hr-" + sfx), retired, "acct-r-" + sfx)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("employee_not_active"));

        String linked = newEmployee("A5b");
        String acct = "acct-l-" + sfx;
        String pid = proposalId(propose(hr("hr-" + sfx), linked, acct).andExpect(status().isCreated()));
        decide(owner(acct), pid, "accept", "{}").andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/employees/" + linked + "/retire")
                        .header("Authorization", hr("hr-" + sfx))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"left\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETIRED"))
                .andExpect(jsonPath("$.data.accountId").value(acct));
        assertThat(dbAccountId(linked)).isEqualTo(acct);
    }

    @Test
    @DisplayName("AC-6: an operator scoped to another department — GET /employees/{id} 403, /approver-ref 200 with the same token")
    void approverRefHasNoDepartmentScope() throws Exception {
        String approver = newEmployee("A6");
        String acct = "acct-cfo-" + sfx;
        String pid = proposalId(propose(hr("hr-" + sfx), approver, acct).andExpect(status().isCreated()));
        decide(owner(acct), pid, "accept", "{}").andExpect(status().isOk());

        String otherDept = service.createDepartment(new CreateDepartmentCommand(FIXTURE_ACTOR,
                "BE044-OTHER-" + sfx, "Other " + sfx, null, LocalDate.of(2026, 1, 1))).id();
        String scoped = token("submitter-" + sfx, "erp.read", List.of(otherDept));

        mockMvc.perform(get(BASE + "/employees/" + approver).header("Authorization", scoped))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DATA_SCOPE_FORBIDDEN"));
        mockMvc.perform(get(BASE + "/employees/" + approver + "/approver-ref").header("Authorization", scoped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(approver))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accountId").value(acct))
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("AC-7: revoke (another HR) · decline (owner) · unlink (owner) — each a state change + one audit row; unlink one employee.changed")
    void revokeDeclineUnlink() throws Exception {
        String emp = newEmployee("A7");
        String acct = "acct-" + sfx;

        String revoked = proposalId(propose(hr("hr-1-" + sfx), emp, acct).andExpect(status().isCreated()));
        decide(hr("hr-2-" + sfx), revoked, "revoke", "{\"reason\":\"wrong person\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.decidedBy").value("hr-2-" + sfx));
        assertThat(auditRows(revoked, "REVOKE_ACCOUNT_LINK")).isEqualTo(1);
        decide(owner(acct), revoked, "accept", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.cause").value("proposal_not_pending"));

        String declined = proposalId(propose(hr("hr-1-" + sfx), emp, acct).andExpect(status().isCreated()));
        decide(owner(acct), declined, "decline", "{\"reason\":\"not me\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
        assertThat(auditRows(declined, "DECLINE_ACCOUNT_LINK")).isEqualTo(1);

        String accepted = proposalId(propose(hr("hr-1-" + sfx), emp, acct).andExpect(status().isCreated()));
        decide(owner(acct), accepted, "accept", "{}").andExpect(status().isOk());
        int eventsBeforeUnlink = employeeEvents(emp);

        mockMvc.perform(post(BASE + "/employees/" + emp + "/account-link/unlink")
                        .header("Authorization", owner(acct))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not my record\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").doesNotExist());
        assertThat(dbAccountId(emp)).isNull();
        assertThat(auditRows(emp, "UNLINK_ACCOUNT")).isEqualTo(1);
        assertThat(employeeEvents(emp)).isEqualTo(eventsBeforeUnlink + 1);

        mockMvc.perform(post(BASE + "/employees/" + emp + "/account-link/unlink")
                        .header("Authorization", hr("hr-1-" + sfx))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.cause").value("not_linked"));

        mockMvc.perform(get(BASE + "/employees/" + emp + "/account-link-proposals")
                        .header("Authorization", hr("hr-1-" + sfx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }
}
