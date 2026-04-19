package com.wms.master.application.query;

import com.example.common.page.PageQuery;

public record ListZonesQuery(
        ListZonesCriteria criteria,
        PageQuery pageQuery) {
}
