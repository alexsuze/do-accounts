package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * 透過 Resend REST API 寄信（新帳號申請通知管理員）。失敗丟 IllegalStateException，由呼叫端決定重試或回錯。
 * 取捨：直接呼叫 HTTP，不裝 Resend SDK。
 */
@Service
public class EmailService {

    private final AppProperties props;
    private final RestClient client = RestClient.create("https://api.resend.com");

    public EmailService(AppProperties props) {
        this.props = props;
    }

    /** @return Resend 回傳的訊息 id */
    public String send(String to, String subject, String html, String text) {
        String key = props.resend().apiKey();
        if (key == null || key.isBlank()) throw new IllegalStateException("尚未設定 RESEND_API_KEY");
        if (to == null || to.isBlank()) throw new IllegalStateException("尚未設定收件 Email");
        try {
            Map<?, ?> res = client.post().uri("/emails")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", props.resend().fromEmail(), "to", List.of(to),
                            "subject", subject, "html", html, "text", text))
                    .retrieve()
                    .body(Map.class);
            return res == null ? null : String.valueOf(res.get("id"));
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Resend 回應 " + e.getStatusCode().value() + "：" + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("寄信失敗：" + e.getMessage(), e);
        }
    }
}
