package com.doaccounts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 金額一律以字串輸出（SD 4.3、ADR-05），避免 JavaScript 浮點誤差。
 * 讀取時字串或數字都接受（Jackson 預設行為）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    SimpleModule bigDecimalAsString() {
        SimpleModule m = new SimpleModule("money");
        m.addSerializer(BigDecimal.class, ToStringSerializer.instance);
        return m;
    }
}
