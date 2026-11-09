package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * 驗證前端 Google 登入拿到的身分憑證（ID token）。
 * 交給 Google 的 tokeninfo 檢查簽章與到期，這裡再確認「發給本系統」與 Email 已驗證。
 * 取捨：tokeninfo 每次登入多一次網路往返，登入次數少不成問題；量大再改成本地驗簽。
 */
@Component
public class GoogleVerifier {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestClient client = RestClient.create("https://oauth2.googleapis.com");
    private final AppProperties props;

    public GoogleVerifier(AppProperties props) {
        this.props = props;
    }

    public record GoogleUser(String sub, String email, String name, String picture) {}

    public GoogleUser verify(String idToken) {
        String clientId = props.auth().googleClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "伺服器尚未設定 GOOGLE_CLIENT_ID");
        }
        if (idToken == null || idToken.isBlank()) throw failed();

        Map<?, ?> info;
        try {
            // 用 POST 傳，憑證不會出現在網址與記錄檔裡
            var form = new LinkedMultiValueMap<String, String>();
            form.add("id_token", idToken);
            info = client.post().uri("/tokeninfo")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw failed();
        }
        if (info == null
                || !clientId.equals(info.get("aud"))
                || !ISSUERS.contains(String.valueOf(info.get("iss")))
                || !"true".equals(String.valueOf(info.get("email_verified")))) {
            throw failed();
        }
        return new GoogleUser(str(info.get("sub")), str(info.get("email")), str(info.get("name")), str(info.get("picture")));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static ResponseStatusException failed() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 登入驗證失敗，請重新登入");
    }
}
