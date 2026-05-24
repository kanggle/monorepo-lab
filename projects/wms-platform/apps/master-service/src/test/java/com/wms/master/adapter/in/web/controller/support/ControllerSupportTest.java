package com.wms.master.adapter.in.web.controller.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.WarehouseStatus;
import org.junit.jupiter.api.Test;

class ControllerSupportTest {

    @Test
    void etag_formatsVersionWithQuotes() {
        assertThat(ControllerSupport.etag(3L)).isEqualTo("\"v3\"");
        assertThat(ControllerSupport.etag(0L)).isEqualTo("\"v0\"");
    }

    @Test
    void sortField_extractsFieldBeforeComma() {
        assertThat(ControllerSupport.sortField("updatedAt,desc")).isEqualTo("updatedAt");
        assertThat(ControllerSupport.sortField("createdAt,asc")).isEqualTo("createdAt");
    }

    @Test
    void sortField_noComma_returnsWholeString() {
        assertThat(ControllerSupport.sortField("updatedAt")).isEqualTo("updatedAt");
    }

    @Test
    void sortDirection_extractsDirectionAfterComma() {
        assertThat(ControllerSupport.sortDirection("updatedAt,desc")).isEqualTo("desc");
        assertThat(ControllerSupport.sortDirection("createdAt,asc")).isEqualTo("asc");
    }

    @Test
    void sortDirection_noComma_defaultsToAsc() {
        assertThat(ControllerSupport.sortDirection("updatedAt")).isEqualTo("asc");
    }

    @Test
    void parseEnum_validValue_returnsEnumConstant() {
        WarehouseStatus result = ControllerSupport.parseEnum("ACTIVE", WarehouseStatus.class, "error");
        assertThat(result).isEqualTo(WarehouseStatus.ACTIVE);
    }

    @Test
    void parseEnum_validValueLowercase_returnsEnumConstant() {
        WarehouseStatus result = ControllerSupport.parseEnum("active", WarehouseStatus.class, "error");
        assertThat(result).isEqualTo(WarehouseStatus.ACTIVE);
    }

    @Test
    void parseEnum_nullInput_returnsNull() {
        assertThat(ControllerSupport.parseEnum(null, WarehouseStatus.class, "error")).isNull();
    }

    @Test
    void parseEnum_blankInput_returnsNull() {
        assertThat(ControllerSupport.parseEnum("  ", WarehouseStatus.class, "error")).isNull();
    }

    @Test
    void parseEnum_invalidValue_throwsValidationException() {
        assertThatThrownBy(() ->
                ControllerSupport.parseEnum("INVALID", WarehouseStatus.class, "status must be ACTIVE or INACTIVE"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status must be ACTIVE or INACTIVE");
    }
}
