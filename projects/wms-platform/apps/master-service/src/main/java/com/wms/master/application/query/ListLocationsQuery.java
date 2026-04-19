package com.wms.master.application.query;

import com.example.common.page.PageQuery;

public record ListLocationsQuery(
        ListLocationsCriteria criteria,
        PageQuery pageQuery) {
}
