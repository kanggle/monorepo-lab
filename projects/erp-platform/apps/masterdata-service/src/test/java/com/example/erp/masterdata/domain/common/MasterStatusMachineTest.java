package com.example.erp.masterdata.domain.common;

import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataReferenceViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domain unit — {@link MasterStatusMachine} retire guard. */
class MasterStatusMachineTest {

    @Test
    @DisplayName("E1: ACTIVE → RETIRED allowed")
    void activeRetireAllowed() {
        assertThatCode(() -> MasterStatusMachine.ensureRetireAllowed(MasterStatus.ACTIVE, "X"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("E1: RETIRED is terminal — re-retire rejected")
    void terminalRetireRejected() {
        assertThatThrownBy(() -> MasterStatusMachine.ensureRetireAllowed(MasterStatus.RETIRED, "X"))
                .isInstanceOf(MasterdataReferenceViolationException.class);
    }
}
