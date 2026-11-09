package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import com.doaccounts.model.User;
import com.doaccounts.repository.UserRepository;
import com.doaccounts.service.GoogleVerifier.GoogleUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 登入、驗證通行證、管理員核准 / 停用（SA FR-1，沿用 to-do-schedule）。
 * 有人第一次申請時寄信通知管理員。
 */
@Service
public class UserService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final Set<String> STATUSES = Set.of(User.PENDING, User.ACTIVE, User.DISABLED);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repo;
    private final GoogleVerifier google;
    private final TokenService tokens;
    private final EmailService email;
    private final CategoryService categories;
    private final AppProperties props;

    public UserService(UserRepository repo, GoogleVerifier google, TokenService tokens,
                       EmailService email, CategoryService categories, AppProperties props) {
        this.repo = repo;
        this.google = google;
        this.tokens = tokens;
        this.email = email;
        this.categories = categories;
        this.props = props;
    }

    public record LoginResult(String token, User user) {}

    /** 用 Google 身分憑證登入；第一次登入建立待核准帳號。待核准 / 停用回 403。 */
    public LoginResult login(String credential) {
        GoogleUser g = google.verify(credential);
        User u = repo.findByGoogleSub(g.sub()).orElseGet(User::new);
        boolean isNew = u.id == null;
        if (isNew) {
            u.googleSub = g.sub();
            u.createdAt = Instant.now();
        }
        u.email = g.email();
        u.name = g.name();
        u.picture = g.picture();
        u.lastLoginAt = Instant.now();
        if (isAdminEmail(g.email())) {
            u.admin = true;
            u.status = User.ACTIVE; // 管理員不會被鎖在外面
        }
        repo.save(u);
        if (isNew && User.PENDING.equals(u.status)) notifyAdmin(u);

        if (User.PENDING.equals(u.status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "已送出申請，請等待管理員核准後再登入");
        }
        if (User.DISABLED.equals(u.status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "此帳號已停用，請聯絡管理員");
        }
        categories.ensureDefaults(u.id); // 第一次被核准登入時建立預設分類（SA FR-1.3）
        return new LoginResult(tokens.issue(u.id), u);
    }

    /** 新申請寄信給管理員；寄不出去只記錄，不影響申請。 */
    private void notifyAdmin(User u) {
        String to = props.auth().adminEmail();
        if (to == null || to.isBlank()) return;
        String name = u.name == null || u.name.isBlank() ? u.email : u.name;
        String link = props.frontendUrl() + "#/admin";
        String when = u.createdAt.atZone(ZONE).format(WHEN);
        String html = """
                <div style="font-family:sans-serif;line-height:1.6">
                <h2 style="margin:0 0 12px">do-accounts 有新的使用申請</h2>
                <p>名稱：%s<br>Email：%s<br>申請時間：%s（台北時間）</p>
                <p><a href="%s" style="display:inline-block;padding:10px 18px;border-radius:10px;background:#0E7490;color:#FFFFFF;font-weight:bold;text-decoration:none">前往管理頁審核</a></p>
                </div>""".formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(u.email), when, HtmlUtils.htmlEscape(link));
        String text = "do-accounts 有新的使用申請\n名稱：%s\nEmail：%s\n申請時間：%s（台北時間）\n前往管理頁審核：%s"
                .formatted(name, u.email, when, link);
        try {
            email.send(to.trim(), "[待審核] %s（%s）申請使用 do-accounts".formatted(name, u.email), html, text);
        } catch (IllegalStateException e) {
            log.warn("通知管理員新申請失敗", e);
        }
    }

    /** 通行證有效且帳號使用中才回傳使用者，否則 null。 */
    public User authenticate(String token) {
        String id = token == null ? null : tokens.verify(token);
        if (id == null) return null;
        return repo.findById(id).filter(u -> User.ACTIVE.equals(u.status)).orElse(null);
    }

    public List<User> list(User admin) {
        requireAdmin(admin);
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public User setStatus(User admin, String id, String status) {
        requireAdmin(admin);
        if (!STATUSES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "狀態不正確");
        if (admin.id.equals(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能變更自己的狀態");
        User u = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此使用者"));
        u.status = status;
        return repo.save(u);
    }

    /** 記帳偏好（SD 3.2 PUT /api/auth/me/settings）；欄位為 null 表示不變 */
    public record SettingsRequest(String baseCurrency, String weekStart, Integer budgetWarnPct) {}

    public User updateSettings(User user, SettingsRequest req) {
        User u = repo.findById(user.id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此使用者"));
        User.Settings st = u.settingsOrDefault();
        if (req.baseCurrency() != null) {
            if (!Currencies.supported(req.baseCurrency())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的幣別：" + req.baseCurrency());
            u.baseCurrency = req.baseCurrency();
        }
        if (req.weekStart() != null) {
            if (!Set.of("mon", "sun").contains(req.weekStart())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每週起始日只能是星期一或星期日");
            st.weekStart = req.weekStart();
        }
        if (req.budgetWarnPct() != null) {
            if (req.budgetWarnPct() < 1 || req.budgetWarnPct() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "預算警示門檻需在 1～100 之間");
            st.budgetWarnPct = req.budgetWarnPct();
        }
        u.settings = st;
        return repo.save(u);
    }

    private boolean isAdminEmail(String email) {
        String admin = props.auth().adminEmail();
        return admin != null && !admin.isBlank() && admin.trim().equalsIgnoreCase(email);
    }

    public static void requireAdmin(User u) {
        if (!u.admin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理員可以使用");
    }
}
