package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.employee.link.EmployeeAccountLinkProposal;
import com.example.erp.masterdata.domain.employee.link.LinkProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeAccountLinkProposalJpaRepository
        extends JpaRepository<EmployeeAccountLinkProposal, String> {

    Optional<EmployeeAccountLinkProposal> findByIdAndTenantId(String id, String tenantId);

    Optional<EmployeeAccountLinkProposal> findFirstByEmployeeIdAndTenantIdAndStatus(
            String employeeId, String tenantId, LinkProposalStatus status);

    Page<EmployeeAccountLinkProposal> findByAccountIdAndTenantIdAndStatusOrderByProposedAtDesc(
            String accountId, String tenantId, LinkProposalStatus status, Pageable pageable);

    Page<EmployeeAccountLinkProposal> findByEmployeeIdAndTenantIdOrderByProposedAtDesc(
            String employeeId, String tenantId, Pageable pageable);
}
