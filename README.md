# My Business — POS, Expenses, Balance Sheet & Appointment Manager

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-blue.svg)](https://developer.android.com/jetpack/compose)
[![Offline First](https://img.shields.io/badge/Storage-100%25%20Offline%20Local%20Storage-orange.svg)](https://developer.android.com/training/data-storage/room)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25%20On--Device-success.svg)](#privacy--offline-architecture)

**My Business** is a comprehensive, **100% offline-first** business management, Point of Sale (POS), expense tracker, appointment booking, and continuous financial balance sheet application for Android.

> 🔒 **100% Local Storage**: All business details, product catalogs, customer transactions, appointments, and settings are stored **entirely on your device's local storage** (Room SQLite Database and Encrypted Preferences). **No external Google Spreadsheet or cloud dependencies are required**, giving you zero lag, complete data privacy, and unconditional offline availability.

---

## 🌟 Comprehensive Features & Functions

### 1. 🧾 Point of Sale (POS) & Daily Income
- **Rapid Service & Product Selection**: Select items or services with a single tap, customize quantities (`+` / `-`), or enter custom sales amounts.
- **Pre-configured Salon & Retail Categories**: Includes popular services categorized into Threading, Waxing, Tinting, Facials, Hair Care, and Retail Products.
- **Payment Method Toggle**: Switch between **Cash** and **Card / EFTPOS** for each sale with distinct visual indicators.
- **Instant Tax Invoice**: Automatically calculates totals and displays a formatted tax invoice with your business name, ABN/ACN, address, contact details, date, and line items.
- **Direct Receipt Sharing**: Instantly share receipts with customers via WhatsApp, SMS, Email, or system print.

### 2. 💸 Expense & Bill Management
- **Operational Expenses (`- Expenses`)**: Record day-to-day business costs like consumable supplies, stock purchases, refreshments, and maintenance with custom notes.
- **Fixed Monthly Overheads (`- Bills`)**: Log fixed recurring obligations such as shop rent, electricity, water, internet, and insurance.
- **Real-Time Financial Impact**: All expenses and bills instantly reflect on today's summary and update the continuous balance sheet in real time.

### 3. 📊 Continuous Day-by-Day Balance Sheet & Statement Reports
- **Continuous Cumulative Carry-Over**: The net closing balance of each day carries over directly into the next day's opening balance, maintaining an unbroken cumulative ledger.
- **Dynamic Daily Breakdown Cards**: Expand any calendar date to inspect individual sales, expenses, and bills logged on that date.
- **In-Line Transaction Management**: Edit transaction notes/amounts with the **Pencil icon** or permanently remove mistakes with the **'X' icon** with instant balance recalculations.
- **Flexible Statement Periods**:
  - **Monthly Statements**: View and filter transactions by calendar month and year.
  - **Quarterly Statements**: Financial quarters (Q1, Q2, Q3, Q4) with financial year tracking.
  - **Annual Statements**: Full financial year summaries (e.g., FY 2026-27).
  - **Custom Date Ranges**: Choose any start and end date using the Material 3 Date Range picker.
- **Professional A4 PDF Export**:
  - Generate high-resolution, vector-crisp multi-page PDF statements formatted to standard A4 specifications.
  - Includes your official **Business Profile Header** (Name, ABN, Address, Phone, Email).
  - **Prominent Period Banner**: Features a dark blue banner with bold white text highlighting the selected period context (e.g., `September 2026`, `Q1 (Jun - Aug) FY 2026-27`).
  - **Summary Totals Box**: Clear breakdown of Total Income, Total Expenses, Total Bills, and Overall Net Balance (color-coded green or red).
  - **Detailed Itemized Ledger**: Complete tabular ledger detailing Date, Type, Category / Notes, Income (+), Expenses / Bills (-), and Running Cumulative Balance.
  - Saved directly to your device's public `Downloads` folder for immediate viewing or printing.

### 4. 📅 Smart Appointment & Schedule Manager
- **Client Booking System**: Book client appointments with name, phone number, selected services, date, time slot, duration, and notes.
- **Appointment Status Tracking**: Manage appointments with statuses: `Confirmed`, `Pending`, `Completed`, and `Cancelled`.
- **Customizable Time Slots**: Configure working hours, opening and closing times, slot intervals (e.g., 15m, 30m, 45m, 60m), buffer times, and working days of the week.
- **Automated Client Reminders**:
  - Optional 24-hour advance reminder alarms and notifications.
  - Direct SMS notification dispatch to client phone numbers.
  - Fast WhatsApp / SMS / Phone Call direct action buttons right from each appointment card.
- **Incoming Call Overlay & Caller Identification**:
  - Optional floating overlay during incoming calls that identifies matching clients, displaying their upcoming appointment details right on the call screen.

### 5. 🏷️ On-Device Menu & Product Manager
- **Full In-App Catalog Control**: Create, edit, and organize services and retail products directly inside the app without needing any spreadsheet.
- **Category Organization**: Group items by service types or custom departments.
- **Dynamic Pricing & Search**: Instantly update service rates and search products in real time during POS checkout.

### 6. 📢 Special Offers, Promos & Rich Media Broadcasts
- **Multi-Format Marketing**: Create and broadcast promotional messages with **Text Only**, **Flyer/Image**, **Video Reel**, **GIF Animation**, or **All-in-One (Text + Media)**.
- **Phone Contacts & Client Selection**: Select 1 or multiple recipients directly from your device's address book and past salon client database, with real-time search, contact chips, and "Select All" support.
- **Ready-to-Use Promo Templates**: Instant one-tap templates for Weekend Specials (20% OFF), VIP Loyalty Rewards, and Holiday/Festive Packages automatically populated with your registered business name, phone, and address.
- **Dual Delivery Channels**:
  - **WhatsApp / Universal Share**: Share high-resolution photos, flyers, or video reels with pre-filled promotional messages.
  - **Direct SMS Dispatch**: Fast batch cellular SMS dispatch directly to selected recipients.

### 7. 🎨 Application Themes & Customization
- **Theme Selection**: Seamlessly switch between **System Default**, **Light Mode**, and high-contrast **Dark Mode** via Business Settings.
- **Material Design 3**: Modern, ergonomic UI with dynamic color palettes, rounded cards, and responsive touch targets.

### 8. 💾 Full System Backup & Data Recovery
- **Single-Tap JSON Export**: Exports **everything** across your system into a portable, structured JSON backup file saved in your `Documents` folder:
  - **Business Details**: Business Name, ABN/ACN, Business Address, Phone/Mobile, Email.
  - **Financial Details**: Complete day-by-day sales records, expenses, bills, amounts, categories, notes, dates, and timestamps.
  - **Products & Services**: The entire service and retail catalog, custom prices, and service categories.
  - **Appointments & Bookings**: All past and upcoming client bookings, appointment times, dates, statuses, customer names, phone numbers, and notes.
  - **System Preferences**: Booking settings (slot durations, working hours, working days, buffer times), notifications & reminder preferences, and theme mode.
- **Instant JSON Restore**: Seamlessly reload the complete system on a new device or after a factory reset with a single tap, displaying an itemized summary of restored transactions, appointments, products, and profile settings.

---

## 🔒 Local Storage & Data Privacy

- **No Cloud Accounts Required**: No Google sign-in, no API keys, and no Google Apps Script deployment needed.
- **100% Offline Availability**: Fully functional in remote areas, aeroplanes, or retail spaces without Wi-Fi or cellular service.
- **Zero Latency**: Database reads and writes execute in milliseconds directly on SQLite.
- **Complete Privacy**: Your financial figures, customer names, phone numbers, and transaction logs never leave your physical device.

---

## 📱 Quick User Workflow

### Recording Daily Sales (POS)
1. From the Dashboard, tap **+ Daily Income**.
2. Tap services/products from the catalog or search by name.
3. Select **Cash** or **Card / EFTPOS**.
4. Tap **Save Daily Income**.
5. Tap **Share Invoice** to send the itemized tax receipt to your customer via WhatsApp or SMS.

### Logging Expenses & Bills
- Tap **- Expenses** to log inventory purchases, consumable supplies, or salon refreshments.
- Tap **- Bills** to log fixed monthly overheads like rent, electricity, and utilities.

### Viewing Balance Sheet & Exporting Statements
1. Tap **= Balance Sheet** from the Dashboard.
2. Review the cumulative balance and day-by-day cashflow cards.
3. Tap **Download PDF** to export the current statement, or tap the **Calendar icon** to generate Monthly, Quarterly, Annual, or Custom Date Range PDF statements.
4. Your PDF is saved directly to your device's `Downloads` folder.

### Managing Appointments
1. Tap **Appointments** on the Dashboard.
2. Tap **+ Book Appointment** to select a date, time slot, client details, and services.
3. Use the one-tap action buttons to call, SMS, or WhatsApp clients directly.

### Configuring Business Profile & Backups
1. Tap the **Settings** (gear) icon in the top right of the Dashboard.
2. Fill in your **Business Registration Details** (Name, ABN/ACN, Address, Phone, Email) — these will automatically populate the header of all tax receipts and PDF statements.
3. Configure your appointment schedule and working hours.
4. Scroll to **Backup & Recovery** to export or restore your data as a JSON file.

---

## 🛠️ Technical Stack & Architecture

- **Platform**: Android (API 24+)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Language**: 100% Kotlin
- **Architecture**: MVVM + Clean Architecture with Coroutines & StateFlow
- **Local Persistence**: 
  - Room Database (SQLite) with transactional safety
  - Encrypted / DataStore SharedPreferences for profile and configuration settings
- **Document Engine**: Android Canvas `PdfDocument` vector rendering (Standard A4 layout)
- **JSON Serialization**: `kotlinx.serialization` for reliable, type-safe backup/restore

---

## 📦 APK Installation & Releases

The pre-built, ready-to-install debug APK is maintained in the root folder of this project:
- **Current Release**: `MyBusiness-v5.9-debug.apk`
- **Installation**: Copy the APK file to your Android phone or tablet, open it using your file manager, and confirm installation.
