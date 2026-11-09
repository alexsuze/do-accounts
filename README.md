# do-accounts

個人記帳理財系統（新版），前身為 [alexsuze/money](https://github.com/alexsuze/money)。

目前處於**規劃階段**，請先閱讀：

- [docs/SA.md](./docs/SA.md)：系統分析（需求、使用案例、資料實體）
- [docs/SD.md](./docs/SD.md)：系統設計（架構、資料庫、API、核心模組、開發階段）

## 與舊版的主要差異

- 加入登入驗證，資料依使用者隔離
- 資料庫改用 PostgreSQL，以「交易＋分錄」模型推導帳戶餘額，不再需要手動同步
- 金額使用定點小數，匯率按日保存
- 新增投資管理、CSV 匯入匯出、JSON 備份
