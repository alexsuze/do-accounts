package com.doaccounts.service;

import com.doaccounts.model.Category;
import com.doaccounts.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 收支分類（SA FR-4、SD 3.5）。兩層：大類 → 子類。系統分類不可刪除。只能操作自己的分類。
 */
@Service
public class CategoryService {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern ICON = Pattern.compile("^[a-z0-9-]{1,30}$");

    private final CategoryRepository repo;
    private final LedgerQueries ledger;

    public CategoryService(CategoryRepository repo, LedgerQueries ledger) {
        this.repo = repo;
        this.ledger = ledger;
    }

    public record CategoryRequest(String kind, String parentId, String name, String icon, String color) {}

    /** 依類型 → 順序；子類緊接在大類之後由前端組樹 */
    public List<Category> list(String userId) {
        return repo.findByUserId(userId).stream()
                .sorted(Comparator.comparing((Category c) -> c.kind).thenComparingInt(c -> c.order))
                .toList();
    }

    public Category get(String userId, String id) {
        return repo.findById(id == null ? "" : id).filter(c -> userId.equals(c.userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此分類"));
    }

    /** 該使用者某類型的系統分類（例如轉帳手續費預設用 expense / fee） */
    public Category system(String userId, String kind, String key) {
        return repo.findByUserId(userId).stream()
                .filter(c -> kind.equals(c.kind) && key.equals(c.systemKey)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少系統分類，請重新登入"));
    }

    /** 分類本身＋子分類的 id（篩選大類時包含子類） */
    public List<String> withChildren(String userId, String id) {
        List<String> ids = new ArrayList<>();
        ids.add(get(userId, id).id);
        repo.findByUserId(userId).stream().filter(c -> id.equals(c.parentId)).forEach(c -> ids.add(c.id));
        return ids;
    }

    public Category create(String userId, CategoryRequest req) {
        Category c = new Category();
        c.userId = userId;
        if (!Category.INCOME.equals(req.kind()) && !Category.EXPENSE.equals(req.kind())) throw bad("分類類型需為收入或支出");
        c.kind = req.kind();
        c.parentId = checkParent(userId, c, req.parentId());
        apply(userId, c, req);
        c.order = siblings(userId, c).stream().mapToInt(x -> x.order + 1).max().orElse(0);
        return repo.save(c);
    }

    /** 不能改類型；上層只能是同類型的大類，且自己有子類時不能變成子類 */
    public Category update(String userId, String id, CategoryRequest req) {
        Category c = get(userId, id);
        if (req.kind() != null && !req.kind().equals(c.kind)) throw bad("不能變更分類的收入／支出類型");
        String parent = checkParent(userId, c, req.parentId());
        if (!Objects.equals(parent, c.parentId)) {
            if (c.systemKey != null) throw bad("系統分類不能移動到其他分類底下");
            c.parentId = parent;
            c.order = siblings(userId, c).stream().filter(x -> !x.id.equals(c.id)).mapToInt(x -> x.order + 1).max().orElse(0);
        }
        apply(userId, c, req);
        return repo.save(c);
    }

    /** ids 為同一層（同類型、同上層）的新順序 */
    public List<Category> reorder(String userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) throw bad("順序不能是空的");
        Category first = get(userId, ids.get(0));
        List<Category> same = siblings(userId, first);
        if (ids.size() != same.size() || !ids.containsAll(same.stream().map(c -> c.id).toList())) {
            throw bad("分類順序與現有分類不符，請重新整理");
        }
        same.forEach(c -> c.order = ids.indexOf(c.id));
        repo.saveAll(same);
        return list(userId);
    }

    /**
     * 刪除分類（含子類）。有交易時必須指定 moveTo，交易搬過去後才刪除（SA FR-4.3）。
     * 系統分類不可刪除（SA FR-4.4）。
     */
    public void delete(String userId, String id, String moveTo) {
        Category c = get(userId, id);
        if (c.systemKey != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "系統分類不能刪除");
        List<Category> children = repo.findByUserId(userId).stream().filter(x -> id.equals(x.parentId)).toList();
        if (children.stream().anyMatch(x -> x.systemKey != null)) throw new ResponseStatusException(HttpStatus.CONFLICT, "底下有系統分類，不能刪除");
        List<String> ids = new ArrayList<>(children.stream().map(x -> x.id).toList());
        ids.add(id);
        long used = ledger.countUsingCategories(userId, ids);
        if (used > 0) {
            if (moveTo == null || moveTo.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "此分類有 " + used + " 筆交易，請選擇要移到哪個分類");
            }
            Category target = get(userId, moveTo);
            if (!target.kind.equals(c.kind)) throw bad("只能移到同為" + (c.kind.equals(Category.INCOME) ? "收入" : "支出") + "的分類");
            if (ids.contains(target.id)) throw bad("不能移到要刪除的分類或它的子分類");
            ledger.moveCategories(userId, ids, target.id);
        }
        repo.deleteAll(children);
        repo.delete(c);
    }

    /** 第一次登入時建立預設分類（SA FR-1.3）；已有分類就不動 */
    public void ensureDefaults(String userId) {
        if (repo.countByUserId(userId) > 0) return;
        int[] order = {0};
        for (Object[] d : DEFAULTS) {
            String kind = (String) d[0];
            Category p = make(userId, kind, null, (String) d[1], (String) d[2], (String) d[3], (String) d[4], order[0]++);
            repo.save(p);
            String[] subs = (String[]) d[5];
            for (int i = 0; i < subs.length; i++) {
                repo.save(make(userId, kind, p.id, subs[i], (String) d[2], (String) d[3], null, i));
            }
        }
    }

    /** {kind, 名稱, 圖示, 顏色, system_key, 子類} */
    private static final Object[][] DEFAULTS = {
            {Category.EXPENSE, "餐飲", "utensils", "#22D3EE", null, new String[]{"早餐", "午餐", "晚餐", "飲料", "食材"}},
            {Category.EXPENSE, "交通", "bus", "#A78BFA", null, new String[]{"捷運", "公車", "計程車", "加油", "停車"}},
            {Category.EXPENSE, "居住", "home", "#60A5FA", null, new String[]{"房租", "水電瓦斯", "網路電話"}},
            {Category.EXPENSE, "日用", "shopping-bag", "#FBBF24", null, new String[]{}},
            {Category.EXPENSE, "娛樂", "gamepad", "#F472B6", null, new String[]{}},
            {Category.EXPENSE, "購物", "shirt", "#FB7185", null, new String[]{}},
            {Category.EXPENSE, "醫療", "heart-pulse", "#34D399", null, new String[]{}},
            {Category.EXPENSE, "學習", "book", "#60A5FA", null, new String[]{}},
            {Category.EXPENSE, "保險", "shield", "#A78BFA", null, new String[]{}},
            {Category.EXPENSE, "手續費", "receipt", "#64748B", Category.SYS_FEE, new String[]{}},
            {Category.EXPENSE, "餘額調整", "scale", "#64748B", Category.SYS_ADJUSTMENT, new String[]{}},
            {Category.EXPENSE, "其他", "ellipsis", "#64748B", Category.SYS_OTHER, new String[]{}},
            {Category.INCOME, "薪資", "briefcase", "#34D399", null, new String[]{}},
            {Category.INCOME, "獎金", "gift", "#22D3EE", null, new String[]{}},
            {Category.INCOME, "利息", "piggy-bank", "#60A5FA", null, new String[]{}},
            {Category.INCOME, "投資損益", "trending-up", "#64748B", Category.SYS_INVESTMENT_PNL, new String[]{}},
            {Category.INCOME, "餘額調整", "scale", "#64748B", Category.SYS_ADJUSTMENT, new String[]{}},
            {Category.INCOME, "其他", "ellipsis", "#64748B", Category.SYS_OTHER, new String[]{}},
    };

    private static Category make(String userId, String kind, String parentId, String name, String icon, String color, String sys, int order) {
        Category c = new Category();
        c.userId = userId;
        c.kind = kind;
        c.parentId = parentId;
        c.name = name;
        c.icon = icon;
        c.color = color;
        c.systemKey = sys;
        c.order = order;
        return c;
    }

    private List<Category> siblings(String userId, Category c) {
        return repo.findByUserId(userId).stream()
                .filter(x -> x.kind.equals(c.kind) && Objects.equals(x.parentId, c.parentId)).toList();
    }

    private String checkParent(String userId, Category self, String parentId) {
        if (parentId == null || parentId.isBlank()) return null;
        Category p = get(userId, parentId);
        if (p.parentId != null) throw bad("只能有兩層分類，上層必須是大類");
        if (!p.kind.equals(self.kind)) throw bad("上層分類的收入／支出類型不同");
        if (p.systemKey != null) throw bad("系統分類底下不能再加子分類");
        if (self.id != null) {
            if (p.id.equals(self.id)) throw bad("上層分類不能是自己");
            boolean hasChildren = repo.findByUserId(userId).stream().anyMatch(x -> self.id.equals(x.parentId));
            if (hasChildren) throw bad("這個分類底下還有子分類，不能變成子分類");
        }
        return p.id;
    }

    private void apply(String userId, Category c, CategoryRequest req) {
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty() || name.length() > 30) throw bad("分類名稱需為 1～30 字");
        boolean dup = siblings(userId, c).stream().anyMatch(x -> !x.id.equals(c.id) && x.name.equals(name));
        if (dup) throw new ResponseStatusException(HttpStatus.CONFLICT, "同一層已有分類「" + name + "」");
        if (req.color() == null || !HEX.matcher(req.color()).matches()) throw bad("顏色格式錯誤");
        if (req.icon() != null && !ICON.matcher(req.icon()).matches()) throw bad("圖示代碼格式錯誤");
        c.name = name;
        c.color = req.color().toUpperCase();
        c.icon = req.icon();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
