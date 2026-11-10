# do-accounts

個人記帳理財系統（新版），前身為 [alexsuze/money](https://github.com/alexsuze/money)；技術架構比照 [alexsuze/to-do-schedule](https://github.com/alexsuze/to-do-schedule)。

- [docs/SA.md](./docs/SA.md)：系統分析（需求、使用案例、資料實體）
- [docs/SD.md](./docs/SD.md)：系統設計（架構、MongoDB 集合、API、核心模組、前端設計、開發階段）
- [docs/開發進度.md](./docs/開發進度.md)：目前進度與下一步
- 頁面示意圖：https://claude.ai/artifact/H4g7zeUWK4nhE9SA4ET5vM

## Repo 結構

```
do-accounts/
├── docs/                # SA、SD、開發進度
├── frontend/            # Vite + Vue 3 + Pinia + Tailwind CSS v4 + Reka UI
├── backend/             # Java 21 + Spring Boot 4（Maven Wrapper）
└── .github/workflows/   # 前端 GitHub Pages 部署、CI
```

## 本機開發

需要：Node.js 22+、Java 21+（backend 內含 Maven Wrapper）、MongoDB 連線字串（Atlas 或本機）。

### 後端
```bash
cd backend
# 設定環境變數（見 backend/.env.example）；本機請用 MONGODB_DATABASE=do_accounts_dev，不要連正式資料庫
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```
- `GET http://localhost:8080/api/ping` 不需登入
- `GET http://localhost:8080/actuator/health/liveness` 應回 `{"status":"UP"}`

### 前端
```bash
cd frontend
cp .env.example .env.local      # 填 API 網址與 Google 用戶端 ID
npm install
npm run dev                     # http://localhost:5173
```
用 `ADMIN_EMAIL` 的 Google 帳號登入後，總覽頁的「連線狀態」兩項都是正常，代表前後端與資料庫皆已接通。

## 部署

### MongoDB Atlas（與其他專案共用 cluster）
1. 沿用現有 cluster，新版使用資料庫 `do_accounts`（正式）與 `do_accounts_dev`（開發）。
2. Database Access 新增專用使用者，Specific Privileges 只給 `readWrite@do_accounts`、`readWrite@do_accounts_dev`。
3. 用這個使用者的連線字串作為 `MONGODB_URI`。

### Google 登入
OAuth 用戶端 ID 的「已授權的 JavaScript 來源」需包含 `https://alexsuze.github.io` 與 `http://localhost:5173`。

### 後端 → Railway
1. 新服務 → Deploy from GitHub repo → 選此 repo，Settings → **Root Directory** 設為 `backend`。
2. Variables：

   | 變數 | 說明 |
   |------|------|
   | `MONGODB_URI` | Atlas 連線字串（專用使用者） |
   | `MONGODB_DATABASE` | `do_accounts` |
   | `GOOGLE_CLIENT_ID` | Google OAuth 用戶端 ID |
   | `ADMIN_EMAIL` | 管理員的 Google Email |
   | `JWT_SECRET` | 通行證簽章密鑰，至少 32 字元；可填 `${{ secret(64) }}` 自動產生。未設定時正式環境拒絕啟動 |
   | `CORS_ALLOWED_ORIGIN` | `https://alexsuze.github.io` |
   | `FRONTEND_URL` | `https://alexsuze.github.io/do-accounts/` |
   | `RESEND_API_KEY`、`RESEND_FROM_EMAIL` | 選用：新帳號申請通知信、每月報表 Email（預設寄件地址只能寄給 Resend 帳號本人；要寄給別人需在 Resend 驗證網域） |

3. push 到 `main` 即自動部署（`backend/railway.toml`）。

### 前端 → GitHub Pages
1. Settings → Pages → Source 選 **GitHub Actions**。
2. Settings → Secrets and variables → Actions：`VITE_API_BASE_URL`（Railway 網址）、`VITE_GOOGLE_CLIENT_ID`。
3. push 到 `main` 且 `frontend/` 有變動時自動部署到 `https://alexsuze.github.io/do-accounts/`。

## 安全
倉庫可能公開：密碼、連線字串、`JWT_SECRET`、API 金鑰只放 Railway Variables／GitHub Secrets，`.env.example` 只放假值。
