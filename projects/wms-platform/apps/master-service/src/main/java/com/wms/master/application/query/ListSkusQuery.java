package com.wms.master.application.query;

import com.example.common.page.PageQuery;

public record ListSkusQuery(
        ListSkusCriteria criteria,
        PageQuery pageQuery) {
}
