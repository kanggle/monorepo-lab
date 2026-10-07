package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.common.page.PageResult;
import com.example.common.persistence.DataIntegrityViolations;
import com.example.erp.masterdata.domain.employee.link.EmployeeAccountLinkProposal;
import com.example.erp.masterdata.domain.employee.link.LinkProposalStatus;
import com.example.erp.masterdata.domain.employee.link.repository.EmployeeAccountLinkProposalRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmployeeAccountLinkProposalRepositoryImpl implements EmployeeAccountLinkProposalRepository {

    private final EmployeeAccountLinkProposalJpaRepository jpa;

    /**
     * {@code saveAndFlush} so the {@code uq_link_proposals_one_pending} index (V3) is evaluated
     * inside the use case, where its violation can still be answered as the contract's 409. The
     * only unique index this insert can trip besides the PK (a fresh UUIDv7) is that one.
     */
    @Override
    public EmployeeAccountLinkProposal insertPending(EmployeeAccountLinkProposal proposal) {
        try {
            return jpa.saveAndFlush(proposal);
        } catch (DataIntegrityViolationException e) {
            if (DataIntegrityViolations.isUniqueViolation(e)) {
                throw new EmployeeLinkConflictException(EmployeeLinkConflictException.PROPOSAL_PENDING,
                        "Employee " + proposal.getEmployeeId() + " already has a PENDING link proposal");
            }
            throw e;
        }
    }

    @Override
    public EmployeeAccountLinkProposal save(EmployeeAccountLinkProposal proposal) {
        return jpa.save(proposal);
    }

    @Override
    public Optional<EmployeeAccountLinkProposal> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<EmployeeAccountLinkProposal> findPendingByEmployeeId(String employeeId, String tenantId) {
        return jpa.findFirstByEmployeeIdAndTenantIdAndStatus(employeeId, tenantId, LinkProposalStatus.PENDING);
    }

    @Override
    public PageResult<EmployeeAccountLinkProposal> findPendingByAccountId(String accountId, String tenantId,
                                                                          int page, int size) {
        return toPageResult(jpa.findByAccountIdAndTenantIdAndStatusOrderByProposedAtDesc(
                accountId, tenantId, LinkProposalStatus.PENDING, PageRequest.of(page, size)), page, size);
    }

    @Override
    public PageResult<EmployeeAccountLinkProposal> findByEmployeeId(String employeeId, String tenantId,
                                                                    int page, int size) {
        return toPageResult(jpa.findByEmployeeIdAndTenantIdOrderByProposedAtDesc(
                employeeId, tenantId, PageRequest.of(page, size)), page, size);
    }

    private static PageResult<EmployeeAccountLinkProposal> toPageResult(
            Page<EmployeeAccountLinkProposal> p, int page, int size) {
        return new PageResult<>(p.getContent(), page, size, p.getTotalElements(), p.getTotalPages());
    }
}
