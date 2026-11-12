package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 某一天盤中價的更新狀態（SD 4.11），全站一天一筆，_id 為日期。用來在畫面顯示「盤中價更新失敗（11:05）」。
 */
@Document("intraday_status")
public class IntradayStatus {

    /** YYYY-MM-DD */
    @Id
    public String id;
    /** 最後一次成功的時間 HH:mm */
    @Field("last_success")
    public String lastSuccess;
    /** 最後一次失敗的時間 HH:mm；成功後清空 */
    @Field("last_failure")
    public String lastFailure;
}
