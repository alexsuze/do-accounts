# do-accounts

個人記帳理財系統（新版），前身為 [alexsuze/money](https://github.com/alexsuze/money)；技術架構比照 [alexsuze/to-do-schedule](https://github.com/alexsuze/to-do-schedule)。

目前處於**規劃階段**，請先閱讀：

- [docs/SA.md](./docs/SA.md)：系統分析（需求、使用案例、資料實體）
- [docs/SD.md](./docs/SD.md)：系統設計（架構、MongoDB 集合、API、核心模組、前端設計、開發階段）
- 頁面示意圖：https://claude.ai/artifact/H4g7zeUWK4nhE9SA4ET5vM

## 技術架構

| 層 | 技術 |
|---|---|
| 前端 | Vite + Vue 3 + Pinia + Tailwind CSS v4 + Reka UI → GitHub Pages |
| 後端 | Java 21 + Spring Boot 4 → Railway |
| 資料庫 | MongoDB Atlas |
| 登入 | Google 登入＋管理員核准 |

## 與舊版的主要差異

- 加入登入驗證，資料依使用者隔離
- 「交易＋分錄」模型，帳戶餘額由分錄推導，不再需要手動同步
- 金額使用 `Decimal128` 精確小數，匯率按日保存
- 新增投資管理、CSV 匯入匯出、JSON 備份
