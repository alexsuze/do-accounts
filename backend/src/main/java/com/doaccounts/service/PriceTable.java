package com.doaccounts.service;

import com.doaccounts.model.Price;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一段期間的收盤價，在記憶體中查找：取該天或之前最近一天；同一天使用者的手動價優先（與 RateTable 相同規則）。
 */
public final class PriceTable {

    private final Map<String, TreeMap<String, Price>> bySecurity = new HashMap<>();

    public PriceTable(Collection<Price> system, Collection<Price> manual) {
        for (Price p : system) put(p);
        for (Price p : manual) put(p);
    }

    private void put(Price p) {
        if (p.close == null || p.close.signum() <= 0) return;
        bySecurity.computeIfAbsent(p.securityId, k -> new TreeMap<>()).put(p.date, p);
    }

    /** 該天或之前最近一天的報價；沒有回 null（還沒有任何報價時，市值以成本計） */
    public Price at(String securityId, String date) {
        TreeMap<String, Price> m = bySecurity.get(securityId);
        if (m == null) return null;
        var e = m.floorEntry(date);
        return e == null ? null : e.getValue();
    }
}
