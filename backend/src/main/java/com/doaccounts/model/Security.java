package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Set;

/**
 * 證券（SD 2.6），全站共用、不分使用者。唯一鍵 { market, symbol }。
 */
@Document("securities")
public class Security {

    public static final Set<String> MARKETS = Set.of("TW", "TWO", "US", "FUND", "CRYPTO", "OTHER");
    public static final Set<String> TYPES = Set.of("stock", "etf", "fund", "bond", "crypto", "other");

    @Id
    public String id;
    /** TW 上市 / TWO 上櫃 / US / FUND / CRYPTO / OTHER */
    public String market;
    public String symbol;
    public String name;
    public String currency;
    @Field("asset_type")
    public String assetType;
}
