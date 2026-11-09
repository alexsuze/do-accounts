# CLAUDE.md — do-accounts

個人記帳理財系統（新版），前身為 alexsuze/money（不遷移舊資料）。技術架構比照 alexsuze/to-do-schedule。
使用者以**繁體中文**溝通；說明時用白話，並提供 A/B 選項。

## 必讀文件
- `docs/SA.md`：需求（功能、使用案例）。任何功能實作以此為準。
- `docs/SD.md`：設計（架構、MongoDB 集合、API、核心演算法、開發階段）。**新增或修改欄位時同步更新 SD 第 2 章。**
- `docs/開發進度.md`：目前進度、已完成項目、下一步。**每完成一個階段都要更新。**

## 開發指令
```bash
cd backend && ./mvnw spring-boot:run          # http://localhost:8080（本機沒有 MongoDB 時 /api/ping 仍可用）
cd backend && ./mvnw -B test                   # 後端測試（不需要 MongoDB）
cd frontend && npm install && npm run dev      # http://localhost:5173
cd frontend && npm test                        # utils 純函式測試
cd frontend && npm run build                   # 驗證可建置
```

## 技術架構
| 層 | 技術 | 部署 |
|----|------|------|
| 前端 | Vite + Vue 3 + Pinia + Vue Router（hash 模式）＋ axios；**Tailwind CSS v4 ＋ Reka UI**（不用 Quasar） | GitHub Pages（GitHub Actions） |
| 後端 | Java 21 + Spring Boot 4（Maven Wrapper） | Railway，Root Directory = `backend` |
| 資料庫 | MongoDB Atlas 免費層 | 環境變數 `MONGODB_URI` |
| 登入 | Google 登入 → `/api/auth/google` 發通行證；`AuthFilter` 檢查 `Authorization: Bearer`；新帳號需管理員核准 | `GOOGLE_CLIENT_ID`、`ADMIN_EMAIL`、`JWT_SECRET` |

## 帳務核心規則（勿違反）
- 一筆交易 = 一份 `transactions` 文件，分錄內嵌在 `entries`；一次記帳只寫一份文件。
- 帳戶**不存餘額**：餘額 = `opening_balance` + 分錄加總。
- 金額用 `BigDecimal`，Mongo 欄位必加 `@Field(targetType = FieldType.DECIMAL128)`；API 金額以字串傳遞。
- 交易日期存 `YYYY-MM-DD` 字串，以範圍條件查詢，不用正規表達式。

## UI 規則
- 版面以頁面示意圖為準：https://claude.ai/artifact/H4g7zeUWK4nhE9SA4ET5vM；設計 tokens 見 SD 5.3。
- 顏色只用 token class（`bg-panel`、`text-muted`、`text-accent`…），不寫死色碼；深淺色靠 `<html class="dark">` 切換。
- 數字（金額、日期、匯率、股數）一律等寬字並右對齊；金額必帶 `+`／`−` 號。
- 可重複的樣式做成 `components/ui/` 元件，不大量使用 `@apply`；對話框、選單、日期選擇用 Reka UI。
- 點擊目標至少 44px；圖示按鈕必有 `aria-label`；不使用 emoji 當圖示。

## 慣例（同 to-do-schedule）
- API 路徑以 `/api/` 開頭；JSON 與 Mongo 欄位一律 snake_case。
- 前端一律透過 `frontend/src/services/api.js` 呼叫後端；localStorage key 用 `do-accounts:` 前綴。
- controller 以 `@RequestAttribute(AuthFilter.USER) User` 取得使用者並傳給 service；別人的資料一律回 404。
- 業務錯誤丟 `ResponseStatusException` 並帶中文訊息（Problem Details，訊息在 `detail`）。
- 時間戳記以 UTC 存。
- 文件與 UI 文字使用繁體中文。

## 安全（倉庫可能公開）
- 密碼、連線字串、`JWT_SECRET`、API 金鑰只放 Railway Variables／GitHub Secrets；`.env.example` 只放假值。
- 權限一律由後端把關，前端不可放機密。
