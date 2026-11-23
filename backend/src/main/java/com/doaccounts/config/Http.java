package com.doaccounts.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 對外 HTTP 連線（證交所、櫃買中心、匯率、Resend、Google）一律從這裡建立，帶連線與讀取時限。
 * 沒有時限時，對方不回應會讓連線永遠卡住；報價更新是 synchronized，卡住一次之後的排程與手動更新全部排隊逾時。
 */
public final class Http {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    /** 共用一個（內含連線池）；每次呼叫都新建 HttpClient 會一直開新的執行緒與連線 */
    private static final RestClient DEFAULT = client(CONNECT_TIMEOUT, READ_TIMEOUT);

    private Http() {}

    public static RestClient client() {
        return DEFAULT;
    }

    public static RestClient client(String baseUrl) {
        return builder(CONNECT_TIMEOUT, READ_TIMEOUT).baseUrl(baseUrl).build();
    }

    static RestClient client(Duration connect, Duration read) {
        return builder(connect, read).build();
    }

    private static RestClient.Builder builder(Duration connect, Duration read) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(connect).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory);
    }
}
