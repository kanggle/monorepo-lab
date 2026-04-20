package com.wms.master.application.query;

import com.example.common.page.PageQuery;

public record ListLotsQuery(
        ListLotsCriteria criteria,
        PageQuery pageQuery) {
}
