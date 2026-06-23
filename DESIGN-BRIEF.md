# Expense Tracker Android Design Brief

## Purpose

This document summarizes the current product state of the app and translates it into a UI and theme design brief. It is intended for a product designer or UI designer preparing the visual system, key flows, and interaction patterns for the Android application.

The app is already functional as a working prototype. The next design phase should focus on improving clarity, trust, consistency, and usability without changing the core product model.

## Product summary

The app is a personal finance and expense tracking application for Android with a strong focus on:

- SMS-based transaction import
- manual transaction entry
- receipt OCR-assisted entry
- review and approval of uncertain imports
- source-aware accounting
- configurable budget-cycle reporting

The product is not only a passive expense logger. It is designed to act as a semi-automated financial assistant that captures transactions from SMS, separates sources properly, and lets the user confirm uncertain records.

## Product principles

The design should support these product principles:

- Trust before automation
- Fast manual correction when automation is uncertain
- Clear separation between income, spending, transfers, and card activity
- Low-friction daily use
- Personal finance feel, not enterprise dashboard feel
- Calm and readable visual language

## Core navigation

The current app has five primary tabs:

- `Dashboard`
- `Transactions`
- `Cards`
- `Review`
- `Settings`

There is also a floating `+` action button that opens the manual transaction entry bottom sheet. This button is intentionally hidden on `Review` and `Settings`.

## Key workflows

### SMS import

The app imports SMS transactions from supported senders and patterns. Known formats are parsed directly. Unknown or irregular transactional SMS can go through an LLM fallback path. Uncertain items are routed to `Review`.

Design implication:
This workflow should feel reliable, observable, and safe. Users need clear progress feedback and confidence that imported items are either saved correctly or queued for review.

### Manual transaction entry

The user can add income or expense entries manually through a bottom sheet. `Cash` is seeded as a default source, and other user-added sources are also selectable. Receipt images can be attached or captured using the camera to prefill fields using OCR.

Design implication:
This flow is a frequent entry point and should feel polished, quick, and forgiving.

### Review uncertain items

Unknown-format or uncertain SMS entries are routed into a review queue. Users can open a review item, inspect the raw text, edit fields, select a source and category, save it as a transaction, or discard it.

Design implication:
The review flow is central to user trust. It should feel like an approval workflow, not a debug screen.

### Receipt OCR

Users can attach a receipt from gallery or take a picture using the camera. OCR attempts to extract merchant, amount, and currency. Merchant detection is not always perfect, so the flow now supports merchant candidates when available.

Design implication:
Receipt processing should not auto-commit data too aggressively. It should encourage quick user confirmation.

### Budget-cycle reporting

The app supports a configurable budget cycle start day, such as `26`, instead of forcing a calendar-month view. Dashboard summaries and the Transactions screen “This month” preset follow this budget cycle. Credit card statement cycle is configured separately.

Design implication:
Date context must be visually explicit everywhere. Users should understand what period they are looking at.

## Current screen inventory

### Dashboard

Purpose:
Show a cycle-based summary of finances.

Current content:

- current budget cycle label
- refresh/import action
- spent total
- income total
- net by source
- category spending summary
- recent activity

Current states:

- populated
- empty
- refreshing
- no sources configured

Design goals:

- establish strong information hierarchy
- highlight primary financial summary first
- make cycle dates obvious
- make source net values easy to scan
- support future chart components cleanly

### Transactions

Purpose:
Provide detailed browsing, filtering, searching, and editing of all transactions.

Current content:

- search field
- preset filters: `Today`, `This month`
- direction filter: `All`, `Spending`, `Income`
- custom date filter with `From`, `To`, and `Find`
- totals summary
- editable transaction list

Transaction row currently shows:

- merchant
- source
- category
- date
- amount

Transaction edit allows:

- merchant edit
- amount edit
- source edit
- category edit
- delete

Design goals:

- create a clean financial ledger feel
- keep filtering lightweight and readable
- make row tap affordance obvious
- support dense but calm scanning

### Cards

Purpose:
Show credit-card-only activity based on statement cycle.

Current content:

- statement cycle label
- cycle spend total
- list of card transactions

Current card row shows:

- merchant
- card/source name
- transaction type
- date
- amount

Design goals:

- visually distinguish from general transactions
- feel more statement-like and billing-oriented
- leave room for future due amount / due date / payment summaries

### Review

Purpose:
Display pending items that need manual approval.

Current content:

- list of pending review items
- raw SMS preview
- draft/no-draft state
- review dialog

Review dialog currently supports:

- merchant
- amount
- currency
- source
- category
- save
- discard

Design goals:

- emphasize confidence and safety
- make the difference between raw text and editable extracted fields clear
- support both drafted and undrafted review items gracefully

### Settings

Purpose:
Control app configuration and operational tools.

Current sections:

- backup to Drive
- restore from Drive
- LLM fallback test
- budget cycle start
- SMS import
- data sources
- LLM API configuration
- card statement cutoff day

Design goals:

- reduce density
- group sections more cleanly
- improve scanning
- make advanced sections feel intentional, not cluttered

### Manual entry bottom sheet

Purpose:
Fast transaction creation from anywhere.

Current content:

- merchant/note
- amount
- currency
- income/expense direction
- source selector
- save action
- take picture
- attach receipt
- OCR status text
- optional merchant candidate picker after receipt scan

Design goals:

- very fast input
- clear default state
- strong visual focus on amount and source
- graceful receipt-assisted workflow

## Source model

The current source types are:

- `Cash`
- `Bank`
- `Wallet`
- `Credit card`

Important business behavior:

- sources are user-managed
- `Cash` is seeded as a default source
- bank and credit-card activity under the same institution are separated
- credit cards require sender short code plus last four digits

Design implications:

- source setup must feel understandable
- credit-card source creation must explain why last four digits are required
- source identity should be visually meaningful throughout the app

## Data states that need dedicated UI

The app already has many meaningful states that should be designed intentionally:

- empty dashboard
- empty transactions
- empty cards
- empty review queue
- empty sources beyond Cash
- loading/importing
- refreshing
- OCR scanning in progress
- OCR completed with uncertain merchant
- API test success
- API test failure
- backup success
- backup failure
- LLM fallback result added to Review
- LLM timeout or raw item added to Review

These should not rely only on plain text feedback. They need visually consistent UI states.

## Components needed

The designer should prepare the following component set.

### Foundation

- color palette
- typography system
- spacing scale
- corner radius system
- elevation/shadow guidance
- icon style guidance

### Inputs and controls

- primary button
- secondary button
- destructive button
- chip
- filter chip
- segmented direction selector
- text field
- dropdown field
- search field
- date selector treatment
- inline field validation treatment

### Surfaces

- summary cards
- transaction row card
- review item card
- settings section card
- modal dialog
- bottom sheet
- empty state panel
- loading/progress state
- status callout

### Feedback

- toast/snackbar style
- inline success status
- inline warning status
- inline error status
- confirmation states

## Recommended visual direction

The app should feel like a modern personal finance product rather than a generic data utility.

Recommended qualities:

- clean
- calm
- trustworthy
- compact
- readable
- lightly premium

Avoid:

- overly corporate dashboard styling
- noisy visual density
- gaming-style color saturation
- too many competing highlight colors

The app should prioritize clarity over decoration.

## Tone of the UI

The UI should communicate:

- confidence
- financial clarity
- low stress
- easy correction
- controlled automation

This is especially important because the app deals with uncertain imported data. The user should always feel in control.

## Information architecture priorities

Priority order for design attention should be:

1. Dashboard
2. Manual entry bottom sheet
3. Transactions list and edit flow
4. Review flow
5. Settings restructuring
6. Cards screen refinement

## Key design questions to solve

The next design iteration should answer these questions clearly:

- How should the app distinguish income, spending, transfers, and card activity visually?
- How should cycle dates be surfaced so the reporting period is never ambiguous?
- How should uncertain imported items feel different from confirmed transactions?
- How should source identity appear across dashboard, transactions, cards, and review?
- How should OCR suggestions be presented so correction is fast and not annoying?
- How should dense settings remain understandable?

## Recommended next UI deliverables

The ideal design handoff sequence is:

### Phase 1

- design system foundations
- dashboard
- transactions list
- manual entry bottom sheet

### Phase 2

- review flow
- cards screen
- settings redesign

### Phase 3

- detailed states
- motion guidance
- import/refresh feedback patterns
- OCR review and merchant-candidate treatment

## Functional limitations still present

These are not design blockers, but the designer should know them:

- parser coverage is still growing
- OCR merchant detection is still heuristic
- LLM fallback may still produce no draft
- import feedback is still basic
- charts are still placeholder/simple summaries
- some flows are functionally correct but visually rough

The design should assume these workflows exist and should become more polished over time.

## What the developer will continue building

Planned implementation work after design alignment:

- more SMS parser coverage
- stronger OCR review flow
- better merchant normalization
- better category suggestion and persistence
- improved charts and analytics
- better card statement experience
- more robust review/debug visibility
- full migration safety for future schema changes

## Summary

This product is already beyond a basic expense logger. The design should support a system where automated import, source-aware accounting, OCR capture, and human review work together smoothly.

The designer should optimize for:

- quick reading
- low-friction corrections
- clear periods and source context
- trust in financial data
- polished manual entry and review interactions

