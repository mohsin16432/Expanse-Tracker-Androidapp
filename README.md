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
- UI screens: Dashboard, Transactions (with Today/This month + search), Cards (statement cycle), Review (pending items), Settings (sources + LLM + backup placeholders)

## Next build steps
1. Finish “Review” actions: confirm/discard, create new source, create new parser rule.
2. Add receipt scan flow (camera + OCR) and optional LLM receipt parsing.
3. Implement Google Drive backup/restore end-to-end (sign-in + encryption + upload/download).
4. Add category management UI and auto-categorization rules UI.

