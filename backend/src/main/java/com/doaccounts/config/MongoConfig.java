package com.doaccounts.config;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 資料庫連線設定（沿用 to-do-schedule）：Railway ↔ Atlas 之間的網路設備會悄悄切斷閒置太久的連線，
 * driver 拿到死連線就一直等。
 * - 閒置超過 60 秒的連線主動關掉換新
 * - 連線 / 讀取 / 選伺服器都設上限，拿到壞連線時很快失敗，driver 會自動換連線重試一次
 */
@Configuration
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return b -> b
                .applyToConnectionPoolSettings(p -> p
                        .maxConnectionIdleTime(60, TimeUnit.SECONDS)
                        .maintenanceFrequency(30, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(6, TimeUnit.SECONDS))
                .applyToClusterSettings(c -> c
                        .serverSelectionTimeout(5, TimeUnit.SECONDS));
    }
}
