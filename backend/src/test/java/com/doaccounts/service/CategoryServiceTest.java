package com.doaccounts.service;

import com.doaccounts.model.Category;
import com.doaccounts.model.Account;
import com.doaccounts.service.CategoryService.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分類：預設分類、兩層限制、系統分類、刪除時搬移交易（SA FR-4）。 */
class CategoryServiceTest {

    private final Fixture f = new Fixture();

    private static HttpStatus status(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    private Category named(String kind, String name) {
        return f.categories.stream().filter(c -> c.kind.equals(kind) && c.name.equals(name)).findFirst().orElseThrow();
    }

    @Test
    void defaultsCreatedOnceWithSystemCategories() {
        f.categoryService.ensureDefaults(ME);
        int n = f.categories.size();
        f.categoryService.ensureDefaults(ME);
        assertEquals(n, f.categories.size(), "已有分類就不再建立");
        assertEquals("fee", f.categoryService.system(ME, "expense", "fee").systemKey);
        assertEquals("other", f.categoryService.system(ME, "income", "other").systemKey);
        Category lunch = named("expense", "午餐");
        assertEquals(named("expense", "餐飲").id, lunch.parentId);
        assertTrue(f.categoryService.list(OTHER).isEmpty(), "不會建到別人身上");
    }

    @Test
    void twoLevelsOnlyAndSameKind() {
        f.categoryService.ensureDefaults(ME);
        Category food = named("expense", "餐飲");
        Category lunch = named("expense", "午餐");
        Category salary = named("income", "薪資");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.create(ME, new CategoryRequest("expense", lunch.id, "便當", null, "#22D3EE"))),
                "不能有第三層");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.create(ME, new CategoryRequest("expense", salary.id, "x", null, "#22D3EE"))),
                "上層類型不同");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.update(ME, food.id, new CategoryRequest("expense", named("expense", "交通").id, "餐飲", null, "#22D3EE"))),
                "有子類的大類不能變成子類");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.update(ME, food.id, new CategoryRequest("income", null, "餐飲", null, "#22D3EE"))),
                "不能改類型");
        assertEquals(HttpStatus.CONFLICT, status(() -> f.categoryService.create(ME, new CategoryRequest("expense", food.id, "午餐", null, "#22D3EE"))),
                "同一層不能同名");
        f.categoryService.create(ME, new CategoryRequest("expense", named("expense", "交通").id, "午餐", "bus", "#a78bfa"));
    }

    @Test
    void systemCategoriesCannotBeDeleted() {
        f.categoryService.ensureDefaults(ME);
        assertEquals(HttpStatus.CONFLICT, status(() -> f.categoryService.delete(ME, f.categoryService.system(ME, "expense", "other").id, null)));
    }

    @Test
    void deleteWithTransactionsRequiresMoveTarget() {
        f.categoryService.ensureDefaults(ME);
        Account cash = f.account("錢包", "TWD");
        Category food = named("expense", "餐飲");
        Category lunch = named("expense", "午餐");
        Category transport = named("expense", "交通");
        var t = f.txService.create(ME, expense(cash.id, lunch.id, "100", "2026-11-09"));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, status(() -> f.categoryService.delete(ME, food.id, null)), "子類有交易也要指定搬去哪");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.delete(ME, food.id, named("income", "薪資").id)), "只能搬到同類型");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.delete(ME, food.id, lunch.id)), "不能搬到要刪的子類");

        f.categoryService.delete(ME, food.id, transport.id);
        assertEquals(transport.id, t.categoryId);
        assertFalse(f.categories.contains(food));
        assertFalse(f.categories.contains(lunch), "子類一起刪除");

        Category empty = f.category(ME, "expense", "沒用到");
        f.categoryService.delete(ME, empty.id, null);
    }

    @Test
    void validation() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.create(ME, new CategoryRequest("transfer", null, "x", null, "#22D3EE"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.create(ME, new CategoryRequest("expense", null, "x", null, "red"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.create(ME, new CategoryRequest("expense", null, " ", null, "#22D3EE"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.categoryService.create(ME, new CategoryRequest("expense", null, "x", "Bad Icon!", "#22D3EE"))));
        Category c = f.category(ME, "expense", "x");
        assertEquals("#22D3EE", c.color);
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.categoryService.get(OTHER, c.id)));
    }
}
