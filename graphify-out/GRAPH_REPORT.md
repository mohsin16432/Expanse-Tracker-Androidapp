# Graph Report - .  (2026-06-22)

## Corpus Check
- Corpus is ~29,143 words - fits in a single context window. You may not need a graph.

## Summary
- 487 nodes · 742 edges · 41 communities (39 shown, 2 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 28 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Settings Storage|Settings Storage]]
- [[_COMMUNITY_Review Workflow|Review Workflow]]
- [[_COMMUNITY_Bank Parser Interfaces|Bank Parser Interfaces]]
- [[_COMMUNITY_Transactions Screen|Transactions Screen]]
- [[_COMMUNITY_Product Design Brief|Product Design Brief]]
- [[_COMMUNITY_Transaction Repository|Transaction Repository]]
- [[_COMMUNITY_Transaction DAO|Transaction DAO]]
- [[_COMMUNITY_Manual Entry Screen|Manual Entry Screen]]
- [[_COMMUNITY_Poe LLM Client|Poe LLM Client]]
- [[_COMMUNITY_Dashboard Screen|Dashboard Screen]]
- [[_COMMUNITY_Cards Screen|Cards Screen]]
- [[_COMMUNITY_SMS Ingestion Engine|SMS Ingestion Engine]]
- [[_COMMUNITY_Backup Restore Service|Backup Restore Service]]
- [[_COMMUNITY_Data Source DAO|Data Source DAO]]
- [[_COMMUNITY_Room Enum Converters|Room Enum Converters]]
- [[_COMMUNITY_Receipt OCR Service|Receipt OCR Service]]
- [[_COMMUNITY_Data Source Repository|Data Source Repository]]
- [[_COMMUNITY_App Database Core|App Database Core]]
- [[_COMMUNITY_Parsing Utilities|Parsing Utilities]]
- [[_COMMUNITY_Category DAO|Category DAO]]
- [[_COMMUNITY_Approved SMS Formats|Approved SMS Formats]]
- [[_COMMUNITY_Pending Import DAO|Pending Import DAO]]
- [[_COMMUNITY_Category Repository|Category Repository]]
- [[_COMMUNITY_Pending Import Repository|Pending Import Repository]]
- [[_COMMUNITY_Domain Models|Domain Models]]
- [[_COMMUNITY_LLM Client Interface|LLM Client Interface]]
- [[_COMMUNITY_SMS Classification Rules|SMS Classification Rules]]
- [[_COMMUNITY_SMS History Importer|SMS History Importer]]
- [[_COMMUNITY_SMS Broadcast Receiver|SMS Broadcast Receiver]]
- [[_COMMUNITY_App Icon Branding|App Icon Branding]]
- [[_COMMUNITY_Application Bootstrap|Application Bootstrap]]
- [[_COMMUNITY_User Regex Formats|User Regex Formats]]
- [[_COMMUNITY_Category Detection Rules|Category Detection Rules]]
- [[_COMMUNITY_Dependency Container|Dependency Container]]
- [[_COMMUNITY_SMS Message Model|SMS Message Model]]
- [[_COMMUNITY_Transaction Draft Model|Transaction Draft Model]]

## God Nodes (most connected - your core abstractions)
1. `SettingsRepository` - 23 edges
2. `TransactionDao` - 14 edges
3. `TransactionRepository` - 14 edges
4. `Expense Tracker Android Design Brief` - 14 edges
5. `ManualExpenseScreen()` - 13 edges
6. `TransactionsScreen()` - 12 edges
7. `DashboardScreen()` - 11 edges
8. `DataSourceDao` - 10 edges
9. `SmsIngestor` - 10 edges
10. `RoomConverters` - 9 edges

## Surprising Connections (you probably didn't know these)
- `Dynamic Sources by Short Code` --semantically_similar_to--> `Source Model`  [INFERRED] [semantically similar]
  README.md → DESIGN-BRIEF.md
- `SMS Transaction Import` --semantically_similar_to--> `SMS Import Workflow`  [INFERRED] [semantically similar]
  README.md → DESIGN-BRIEF.md
- `Manual Transaction Entry` --semantically_similar_to--> `Manual Entry Bottom Sheet`  [INFERRED] [semantically similar]
  README.md → DESIGN-BRIEF.md
- `Manual Transaction Entry` --semantically_similar_to--> `Manual Transaction Entry Workflow`  [INFERRED] [semantically similar]
  README.md → DESIGN-BRIEF.md
- `Receipt Scan Flow` --semantically_similar_to--> `Receipt OCR Workflow`  [INFERRED] [semantically similar]
  README.md → DESIGN-BRIEF.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Core Navigation Tabs** — design_dashboard, design_transactions_screen, design_cards_screen, design_review_screen, design_settings_screen [EXTRACTED 1.00]
- **Automation with Human Review** — design_trust_before_automation, design_sms_import, design_review_uncertain_items, design_receipt_ocr, design_controlled_automation [EXTRACTED 1.00]
- **Source-Aware Finance Surfaces** — design_source_model, design_manual_transaction_entry, design_transactions_screen, design_cards_screen, design_review_screen [EXTRACTED 1.00]
- **Expense Tracker Brand Signal** — app_icon_app_icon, app_icon_wallet_metaphor, app_icon_transaction_flow, app_icon_finance_branding [INFERRED 0.84]

## Communities (41 total, 2 thin omitted)

### Community 0 - "Settings Storage"
Cohesion: 0.16
Nodes (9): Flow, Int, List, String, UserSmsRegexFormat, ApprovedSmsFormat, SettingsRepository, SettingsSnapshot (+1 more)

### Community 1 - "Review Workflow"
Cohesion: 0.08
Nodes (24): AppContainer, com, List, PendingImportEntity, String, TransactionDraft, AppContainer, Boolean (+16 more)

### Community 2 - "Bank Parser Interfaces"
Cohesion: 0.07
Nodes (18): SmsMessage, TransactionDraft, Boolean, SmsMessage, String, TransactionDraft, Boolean, SmsMessage (+10 more)

### Community 3 - "Transactions Screen"
Cohesion: 0.14
Nodes (23): android, AppContainer, Boolean, Color, ImageVector, Int, LocalDate, Modifier (+15 more)

### Community 4 - "Product Design Brief"
Cohesion: 0.16
Nodes (23): Expense Tracker Android Design Brief, Budget Cycle Reporting, Cards Screen, Controlled Automation, Dashboard Screen, Manual Entry Bottom Sheet, Manual Transaction Entry Workflow, Receipt OCR Workflow (+15 more)

### Community 5 - "Transaction Repository"
Cohesion: 0.17
Nodes (8): Boolean, Flow, Int, List, Long, String, TransactionEntity, TransactionRepository

### Community 6 - "Transaction DAO"
Cohesion: 0.19
Nodes (7): Flow, Int, List, Long, String, TransactionEntity, TransactionDao

### Community 7 - "Manual Entry Screen"
Cohesion: 0.19
Nodes (20): android, AppContainer, Boolean, DataSourceType, ImageVector, Modifier, String, TransactionDirection (+12 more)

### Community 8 - "Poe LLM Client"
Cohesion: 0.15
Nodes (14): Result, SmsMessage, String, TransactionDraft, ChatCompletionRequest, ChatCompletionResponse, ChatMessage, ChatMessageOut (+6 more)

### Community 9 - "Dashboard Screen"
Cohesion: 0.22
Nodes (18): AppContainer, Color, ImageVector, Int, LocalDate, Long, Modifier, Pair (+10 more)

### Community 10 - "Cards Screen"
Cohesion: 0.19
Nodes (14): androidx, Long, String, AppContainer, Modifier, String, TransactionEntity, CardCycleSummaryCard() (+6 more)

### Community 11 - "SMS Ingestion Engine"
Cohesion: 0.22
Nodes (9): Boolean, com, List, Long, SmsMessage, String, TransactionDraft, UserSmsRegexFormat (+1 more)

### Community 12 - "Backup Restore Service"
Cohesion: 0.24
Nodes (7): Boolean, String, Uri, BackupService, BackupSnapshot, BackupSnapshot, File

### Community 13 - "Data Source DAO"
Cohesion: 0.25
Nodes (5): DataSourceEntity, Flow, List, String, DataSourceDao

### Community 14 - "Room Enum Converters"
Cohesion: 0.24
Nodes (6): DataSourceType, String, TransactionDirection, RoomConverters, PendingImportStatus, TransactionType

### Community 15 - "Receipt OCR Service"
Cohesion: 0.23
Nodes (9): List, Long, Result, String, Uri, Bitmap, InputImage, ReceiptOcrResult (+1 more)

### Community 16 - "Data Source Repository"
Cohesion: 0.27
Nodes (5): DataSourceEntity, Flow, List, String, DataSourceRepository

### Community 17 - "App Database Core"
Cohesion: 0.15
Nodes (8): CategoryDao, DataSourceDao, AppDatabase, migrate(), PendingImportDao, RoomDatabase, SupportSQLiteDatabase, TransactionDao

### Community 18 - "Parsing Utilities"
Cohesion: 0.25
Nodes (6): List, Long, Pair, String, Locale, ParseUtils

### Community 19 - "Category DAO"
Cohesion: 0.31
Nodes (4): CategoryEntity, Flow, List, CategoryDao

### Community 20 - "Approved SMS Formats"
Cohesion: 0.33
Nodes (8): Boolean, SmsMessage, String, TransactionDraft, ApprovedSmsFormat, buildTemplateRegex(), fromApprovedDraft(), replaceFirstIgnoreCase()

### Community 21 - "Pending Import DAO"
Cohesion: 0.28
Nodes (5): Flow, Int, List, PendingImportEntity, PendingImportDao

### Community 22 - "Category Repository"
Cohesion: 0.36
Nodes (4): CategoryEntity, Flow, List, CategoryRepository

### Community 23 - "Pending Import Repository"
Cohesion: 0.28
Nodes (5): Flow, Int, List, PendingImportEntity, PendingImportRepository

### Community 24 - "Domain Models"
Cohesion: 0.22
Nodes (8): CategoryEntity, DataSourceEntity, DataSourceType, PendingImportEntity, PendingImportStatus, TransactionDirection, TransactionEntity, TransactionType

### Community 25 - "LLM Client Interface"
Cohesion: 0.29
Nodes (5): Result, SmsMessage, String, LlmClient, LlmSmsResult

### Community 26 - "SMS Classification Rules"
Cohesion: 0.48
Nodes (3): Boolean, String, SmsClassifier

### Community 27 - "SMS History Importer"
Cohesion: 0.60
Nodes (3): Int, ImportResult, SmsHistoryImporter

### Community 28 - "SMS Broadcast Receiver"
Cohesion: 0.33
Nodes (4): BroadcastReceiver, Context, SmsReceiver, Intent

### Community 29 - "App Icon Branding"
Cohesion: 0.70
Nodes (5): Expense Tracker App Icon, Trustworthy Personal Finance Branding, Rounded Mobile App Icon Container, Transaction Flow Motion, Wallet Metaphor

### Community 30 - "Application Bootstrap"
Cohesion: 0.50
Nodes (3): AppContainer, Application, ExpenseTrackerApp

### Community 31 - "User Regex Formats"
Cohesion: 0.40
Nodes (3): Boolean, String, UserSmsRegexFormat

### Community 32 - "Category Detection Rules"
Cohesion: 0.40
Nodes (3): String, TransactionEntity, CategoryRules

### Community 33 - "Dependency Container"
Cohesion: 0.50
Nodes (3): LlmClient, AppDatabase, AppContainer

## Knowledge Gaps
- **111 isolated node(s):** `Bundle`, `BackupSnapshot`, `File`, `AppDatabase`, `LlmClient` (+106 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppNav()` connect `Review Workflow` to `Dashboard Screen`, `Cards Screen`, `Transactions Screen`, `Manual Entry Screen`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Why does `TransactionsScreen()` connect `Transactions Screen` to `Review Workflow`, `Manual Entry Screen`?**
  _High betweenness centrality (0.017) - this node is a cross-community bridge._
- **Why does `ManualExpenseScreen()` connect `Manual Entry Screen` to `Review Workflow`, `Transactions Screen`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `ManualExpenseScreen()` (e.g. with `TransactionsScreen()` and `AppNav()`) actually correct?**
  _`ManualExpenseScreen()` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Bundle`, `BackupSnapshot`, `File` to the rest of the system?**
  _111 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Review Workflow` be split into smaller, more focused modules?**
  _Cohesion score 0.08275862068965517 - nodes in this community are weakly interconnected._
- **Should `Bank Parser Interfaces` be split into smaller, more focused modules?**
  _Cohesion score 0.06896551724137931 - nodes in this community are weakly interconnected._