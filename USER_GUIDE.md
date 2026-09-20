# My Business — Comprehensive User Guide & Operations Manual

Welcome to **My Business**, an all-in-one business management, Point of Sale (POS), expense tracking, appointment booking, and continuous financial balance sheet application built for Android.

The app is **100% offline-first**: all data, transaction histories, product catalogs, customer appointments, and business profiles are saved directly on **local device storage** (SQLite Room Database & encrypted preferences). No Google Spreadsheet connection, cloud login, or external network is required!

---

## Table of Contents
1. [App Overview & Local Architecture](#1-app-overview--local-architecture)
2. [Quick Start & Business Profile Setup](#2-quick-start--business-profile-setup)
3. [Dashboard & Key Metrics](#3-dashboard--key-metrics)
4. [Point of Sale (POS) & Daily Income](#4-point-of-sale-pos--daily-income)
   - [Product Catalog & Service Items](#product-catalog--service-items)
   - [Adding Items to Cart & Custom Amounts](#adding-items-to-cart--custom-amounts)
   - [Payment Methods & Recording Income](#payment-methods--recording-income)
   - [Tax Invoice & Instant Receipt Sharing](#tax-invoice--instant-receipt-sharing)
5. [Logging Expenses & Operational Costs](#5-logging-expenses--operational-costs)
6. [Logging Bills & Fixed Overheads](#6-logging-bills--fixed-overheads)
7. [Day-by-Day Balance Sheet & PDF Financial Reports](#7-day-by-day-balance-sheet--pdf-financial-reports)
   - [Continuous Cumulative Carry-Over Balance](#continuous-cumulative-carry-over-balance)
   - [Daily Breakdown Cards & Transaction Expansion](#daily-breakdown-cards--transaction-expansion)
   - [Editing and Deleting Transactions](#editing-and-deleting-transactions)
   - [Dynamic Statement Periods (Monthly, Quarterly, Annual, Custom)](#dynamic-statement-periods)
   - [Professional A4 PDF Export](#professional-a4-pdf-export)
8. [Smart Appointment & Schedule Manager](#8-smart-appointment--schedule-manager)
   - [Booking Client Appointments](#booking-client-appointments)
   - [Time Slots & Working Hour Configuration](#time-slots--working-hour-configuration)
   - [Automated Reminders & SMS Notifications](#automated-reminders--sms-notifications)
   - [Incoming Call Identification Overlay](#incoming-call-identification-overlay)
9. [On-Device Menu & Product Catalog Manager](#9-on-device-menu--product-catalog-manager)
10. [Local Backup & Restore (JSON)](#10-local-backup--restore-json)
11. [App Theming (Dark / Light Mode)](#11-app-theming-dark--light-mode)
12. [APK Installation & Versioning](#12-apk-installation--versioning)
13. [Frequently Asked Questions (FAQ)](#13-frequently-asked-questions-faq)

---

## 1. App Overview & Local Architecture

**My Business** empowers small businesses, service providers, salons, trade contractors, and retail shops to manage all daily cashflow with zero latency and absolute privacy:

- **100% Local Storage**: All records live securely on your device inside a local SQLite (Room) database.
- **Zero Cloud or Spreadsheet Dependencies**: No Google Sheets, Google account permissions, or Apps Scripts required.
- **Uncompromised Data Privacy**: Your financial figures, customer details, appointments, and receipts never leave your phone.
- **Full Offline Operation**: Works seamlessly anywhere—even in basements, flight mode, or areas without cellular coverage.

---

## 2. Quick Start & Business Profile Setup

1. Open the **My Business** app.
2. Tap the **Settings** (gear) icon in the top-right corner of the Dashboard.
3. In the **Business Registration Details** section, enter your information:
   - **Business Name**: (e.g., `Sunshine Beauty & Salon`)
   - **ABN / ACN / Tax ID**: (e.g., `ABN 12 345 678 901`)
   - **Business Address**: (e.g., `120 Collins St, Melbourne VIC 3000`)
   - **Phone / Mobile**: (e.g., `+61 400 123 456`)
   - **Email Address**: (e.g., `contact@sunshinebeauty.com`)
4. Tap **Save Profile**.
*(These details are automatically printed on customer Tax Invoices and on all exported A4 PDF balance sheet reports).*

---

## 3. Dashboard & Key Metrics

The home dashboard gives you an instant, high-contrast financial pulse of your business:
- **Net Daily Cash Balance**: Income minus Expenses and Bills for today.
- **Today's Breakdown**: Clear tiles displaying Today's Income, Today's Expenses, and Today's Bills.
- **Quick Action Buttons**:
  - **+ Daily Income**: Open POS checkout to record sales.
  - **- Expenses**: Record day-to-day operational expenses.
  - **- Bills**: Log fixed monthly overhead costs.
  - **= Balance Sheet**: View continuous cumulative cash balance and export PDF statements.
  - **Appointments**: View calendar bookings and client schedules.

---

## 4. Point of Sale (POS) & Daily Income

### Product Catalog & Service Items
- Browse services and items organized by category (e.g., Threading, Waxing, Tinting, Facials, Hair Care, Retail Products).
- Tap any item to instantly add it to the cart.
- Use the `+` and `-` controls to adjust quantities or remove items.

### Adding Items to Cart & Custom Amounts
- If a service or item is not in the list, simply type a **Custom Amount** and optional description note.
- Cart totals update in real-time with subtotal and item count.

### Payment Methods & Recording Income
- Select the payment method: **Cash** or **Card / EFTPOS**.
- Tap **Save Daily Income**. The transaction is immediately committed to local storage.

### Tax Invoice & Instant Receipt Sharing
- After saving, a clean, formatted **Tax Invoice** is generated containing your business profile, invoice number, date/time, itemized services, payment method, and total.
- Tap **Share Invoice** to send the receipt directly to your customer via WhatsApp, SMS, Email, or your printer.

---

## 5. Logging Expenses & Operational Costs

1. Tap **- Expenses** on the Dashboard.
2. Enter the cost amount and a description (e.g., *Salon cleaning supplies, threading thread, facial creams*).
3. Tap **Save Expense**.
4. The expense immediately reduces today's net balance and is logged on the balance sheet.

---

## 6. Logging Bills & Fixed Overheads

1. Tap **- Bills** on the Dashboard.
2. Enter the bill amount and note (e.g., *Shop rent, electric utility, internet, water*).
3. Tap **Save Bill**.
4. Bills are tracked in their own dedicated column and balance calculations.

---

## 7. Day-by-Day Balance Sheet & PDF Financial Reports

### Continuous Cumulative Carry-Over Balance
The balance sheet automatically calculates day-by-day cashflow:
- The closing balance of Day 1 becomes the starting balance for Day 2.
- Net balance reflects all cumulative transactions from day one to the present.

### Daily Breakdown Cards & Transaction Expansion
- Every calendar date with activity is shown as an expandable card.
- Tapping a card reveals all itemized transactions logged on that date with timestamps and payment methods.

### Editing and Deleting Transactions
- **Edit**: Tap the **Pencil icon** on any transaction to modify amounts or notes.
- **Delete**: Tap the **'X' icon** to delete an errant entry. The continuous balance updates automatically.

### Dynamic Statement Periods
Easily filter and generate statements for:
- **Monthly Statements**: Full month financial context (e.g., *September 2026*).
- **Quarterly Statements**: Financial quarters (e.g., *Q1 (Jun - Aug) FY 2026-27*).
- **Annual Statements**: Complete financial year summary (e.g., *FY 2026-27*).
- **Custom Date Ranges**: Select any start and end date using the calendar picker.

### Professional A4 PDF Export
- Tap **Download PDF** to export your statement.
- **Document Features**:
  - Crisp vector layout formatted to standard A4 specifications.
  - **Business Header**: Your registered business name, ABN, address, phone, and email.
  - **Period Subtitle Banner**: A prominent dark blue banner displaying the statement period with centered, bold white text.
  - **Summary Box**: Highlights Total Income, Total Expenses, Total Bills, and Overall Net Balance.
  - **Itemized Ledger Table**: Date, Type, Category / Notes, Income (+), Expenses / Bills (-), and Running Cumulative Balance.
- Saved directly to your device's public `Downloads` folder (`Download/MyBusiness/`).

---

## 8. Smart Appointment & Schedule Manager

### Booking Client Appointments
1. Tap **Appointments** on the Dashboard.
2. Tap **+ Book Appointment**.
3. Enter the client's name, phone number, select services, appointment date, time, duration, and notes.
4. Set initial status (`Confirmed` or `Pending`).

### Time Slots & Working Hour Configuration
In **Settings > Appointment Settings**:
- Set daily opening and closing hours.
- Choose slot intervals (15, 30, 45, or 60 minutes).
- Add buffer times between bookings.
- Enable or disable specific working days of the week.

### Automated Reminders & SMS Notifications
- Enable **24-Hour Advance Reminders** to alert you before upcoming bookings.
- Send instant confirmation or reminder messages to clients via one-tap **WhatsApp**, **SMS**, or **Phone Call** buttons.

### Incoming Call Identification Overlay
- Optional caller ID overlay detects incoming calls from clients who have upcoming appointments and displays their booking details right on your call screen.

---

## 9. On-Device Menu & Product Catalog Manager

- Navigate to **Settings > Menu & Products**.
- **Add Items**: Create custom items with Name, Price, and Category.
- **Edit / Delete**: Update prices or remove outdated services with a tap.
- Any change is saved locally and instantly reflected in the POS checkout screen.

---

## 10. Local Backup & Restore (JSON)

Protect your entire business data against device loss, or transfer records to a new phone:
- **Comprehensive Full Backup**: Backs up **everything** in your application:
  - **Business Profile**: Business name, ABN/ACN, phone number, email, and physical address.
  - **Financial Details**: Complete day-by-day sales records, expenses, bills, categories, timestamps, and amounts.
  - **Products & Services**: Full catalog of items, pricing, and categories.
  - **Appointments & Bookings**: All scheduled, confirmed, and historical customer bookings with customer names, phone numbers, notes, and service selections.
  - **System Preferences**: Booking slot duration, working hours, working days, buffer time, reminder toggles, automated SMS preference, and visual theme selection.
- **Export Backup**: Tap **Settings > Backup & Data Recovery > Export Backup**. A timestamped `.json` file (`MyBusiness_FullBackup_YYYYMMDD_HHMMSS.json`) is safely written to your `Documents` folder.
- **Restore Backup**: Tap **Restore Backup** and select any previously exported `.json` file. The app validates and reloads all transactions, appointments, products, and profile settings with a detailed summary report.

---

## 11. App Theming (Dark / Light Mode)

- Go to **Settings > Appearance / Theme**.
- Choose between **System Default**, **Light Theme**, or **Dark Theme** for eye-safe night-time bookkeeping.

---

## 12. APK Installation & Versioning

The latest pre-compiled, standalone APK is always available in the root folder:
- **Current File**: `MyBusiness-v5.7-debug.apk`
- **Installation**: Transfer the APK to your Android device, tap to install, and grant install-from-unknown-sources permission if prompted.
- **Auto-Versioning**: Every new release automatically increments version numbering (e.g., v5.1 ➔ v5.2 ➔ v5.3).

---

## 13. Frequently Asked Questions (FAQ)

**Q: Do I need an internet connection to use My Business?**  
*A:* No. The app is 100% offline-first. All calculations, database writes, invoice generations, and PDF exports work without any internet or Wi-Fi connection.

**Q: Where are my PDF statements saved?**  
*A:* PDF statements are saved to your device's standard `Downloads` directory (or `Downloads/MyBusiness/`). You can open them with any PDF viewer or send them to your accountant.

**Q: Where are my backup files saved?**  
*A:* JSON backups are saved in your device's `Documents/MyBusiness/` directory.

**Q: Can I transfer my data to a new phone?**  
*A:* Yes! Export a JSON backup on your old phone, send the `.json` file to your new phone (via email, Bluetooth, or Google Drive), and use **Import Backup** in Settings to restore all data.
