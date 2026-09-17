# Expense Tracker (Android)

Premium dark-first personal finance app that imports transactional SMS, supports receipt scan, manual entry, dynamic sources by short code, fast search by merchant/date, and Google Drive backup/restore.

## How to open
1. Open Android Studio
2. `File` → `Open…` → select the `expense-tracker-android` folder
3. Let Android Studio download the required Gradle/Android components during sync.

## Permissions
This project uses `READ_SMS` + `RECEIVE_SMS` for personal use.

## Withdrawal handling
ATM cash withdrawal SMS are **excluded completely**:
- not parsed
- not stored
- not shown anywhere

## LLM fallback (OpenAI-compatible)
Unknown/new SMS formats can be sent to an OpenAI-compatible endpoint for best-effort classification + parsing.

Configure in-app:
- Settings → LLM API → `API URL`
- Settings → LLM API → `API key` (stored encrypted via Android Keystore)
- Settings → LLM API → `Model`

Poe docs: https://creator.poe.com/docs/external-applications/openai-compatible-api

## What’s implemented now
- Project scaffold (Kotlin + Compose + Room)
- Core data models + DAO + repositories
- SMS receiver + bank parsers (HBL / FBL / JazzCash)
- LLM fallback client (chat completions)
- UI screens: Dashboard, Transactions (filters + search), Reports (charts), Cards (statement cycle), Review (pending items), Settings (sources, budgets, categories, LLM, backup)

## Accuracy features
- **Internal transfers** between your own accounts are detected and excluded from Spent and
  Income so a HBL -> Meezan Raast move is not counted twice.
- **Duplicates** captured from both SMS and a push notification are flagged and excluded.
- **Refunds** reduce spending instead of inflating income.
- **Learned merchant rules** remember corrected names and categories and reapply them on import.

## Reports
- Category donut for the current cycle, top merchants, and a 6-month spend/income trend.
- Budget progress with per-category limits, plus over/warning states.
- **Subscriptions**: recurring charges are detected automatically from history
  (same merchant, steady amount, regular interval) and shown with a monthly equivalent.

## Budgets
Set a limit per category (or an overall limit) in Settings. Limits follow your budget cycle
start day. A local notification fires once when spending crosses 80% and again if it goes over.

## Security & data portability
- **App lock**: optionally require the device screen lock (PIN, pattern, password or biometric)
  before the app opens. Uses the platform keyguard, so no biometric permission is needed.
  If the device has no screen lock configured the feature stays off rather than locking you out.
- **CSV export**: export all transactions as a spreadsheet-ready CSV (Settings -> Backup & restore).
  Includes a column explaining why a row is excluded from totals (transfer, duplicate or refund).

## Balances
Record an opening balance per account in Settings -> Account balances. The Dashboard then shows a
running balance for each source. Internal transfers move both balances even though they are
excluded from spend/income.

## Split transactions
A single payment can be divided across categories. Category totals, reports and budgets all
respect splits, so a 10,000 grocery run split across Food and Household counts in both.

## Automatic backup
Enable "Daily automatic backup" in Settings to have the app save a backup to the device once a
day via WorkManager, so data survives a lost phone even if you never tap Export.

## Running the tests
```bash
./gradlew :app:testDebugUnitTest
```

