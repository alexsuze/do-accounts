# CLAUDE.md — do-accounts

個人記帳理財系統（新版），前身為 alexsuze/money（不遷移舊資料）。技術架構比照 alexsuze/to-do-schedule。
使用者以**繁體中文**溝通；說明時用白話，並提供 A/B 選項。

## 必讀文件
- `docs/SA.md`：需求（功能、使用案例）。任何功能實作以此為準。
- `docs/SD.md`：設計（架構、MongoDB 集合、API、核心演算法、開發階段）。**新增或修改欄位時同步更新 SD 第 2 章。**
- `docs/開發進度.md`（開始實作後建立）：**每完成一個階段都要更新。**

## 技術架構
| 層 | 技術 | 部署 |
|----|------|------|
| 前端 | Vite + Vue 3 + Pinia + Vue Router（hash 模式）＋ axios；**UI 元件庫待定**（等 UI 設計確認，見 SD 1.5） | GitHub Pages（GitHub Actions） |
| 後端 | Java 21 + Spring Boot 4（Maven Wrapper） | Railway，Root Directory = `backend` |
| 資料庫 | MongoDB Atlas 免費層 | 環境變數 `MONGODB_URI` |
| 登入 | Google 登入 → `/api/auth/google` 發通行證；`AuthFilter` 檢查 `Authorization: Bearer`；新帳號需管理員核准 | `GOOGLE_CLIENT_ID`、`ADMIN_EMAIL`、`JWT_SECRET` |

## 帳務核心規則（勿違反）
- 一筆交易 = 一份 `transactions` 文件，分錄內嵌在 `entries`；一次記帳只寫一份文件。
- 帳戶**不存餘額**：餘額 = `opening_balance` + 分錄加總。
- 金額用 `BigDecimal`，Mongo 欄位必加 `@Field(targetType = FieldType.DECIMAL128)`；API 金額以字串傳遞。
- 交易日期存 `YYYY-MM-DD` 字串，以範圍條件查詢，不用正規表達式。

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
