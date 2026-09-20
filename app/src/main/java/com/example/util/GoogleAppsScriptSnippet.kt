package com.example.util

object GoogleAppsScriptSnippet {
    val CODE: String = """
/**
 * Google Apps Script Backend for My Business Android App
 * 
 * Features:
 * - Dynamic Monthly Sheets (e.g., Sep26, Oct26, Nov26)
 * - Chronological sorting by Date (Column A) with continuous balance carry-over
 * - Automatic Opening Balance carried forward from previous month
 * - Products Catalog Sheet ("Products") for Point of Sale (POS) item name & price suggestions
 * - Business Profile stored in Sheet1 with formatted Last Updated timestamp (dd-MM-yyyy AM/PM HH:mm)
 * - Balance formula: =SUM(Income) - SUM(Expense & Bills)
 * - Robust date handling supporting dd-MM-yyyy and yyyy-MM-dd
 * - Permanent deletion with balance formula recalculation
 * - PDF Export support
 */

var MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/**
 * Returns month sheet name like "Sep26" from a Date object or string.
 */
function getMonthSheetName(date) {
  var d;
  if (date instanceof Date) {
    d = date;
  } else if (typeof date === "string") {
    var parts = date.trim().split("-");
    if (parts.length === 3) {
      if (parts[0].length === 4) {
        // yyyy-MM-dd
        d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
      } else {
        // dd-MM-yyyy
        d = new Date(parseInt(parts[2], 10), parseInt(parts[1], 10) - 1, parseInt(parts[0], 10));
      }
    } else {
      d = new Date(date);
    }
  } else {
    d = new Date();
  }

  if (isNaN(d.getTime())) d = new Date();
  var monthAbbr = MONTH_NAMES[d.getMonth()];
  var yearTwoDigits = String(d.getFullYear()).slice(-2);
  return monthAbbr + yearTwoDigits;
}

/**
 * Returns previous month's sheet name. E.g. "Sep26" -> "Aug26", "Jan26" -> "Dec25"
 */
function getPreviousMonthSheetName(sheetName) {
  var abbr = sheetName.slice(0, 3);
  var yr = parseInt(sheetName.slice(3), 10);
  var idx = MONTH_NAMES.indexOf(abbr);
  if (idx === -1) return null;

  if (idx === 0) {
    return "Dec" + (yr - 1 < 10 ? "0" + (yr - 1) : String(yr - 1));
  } else {
    return MONTH_NAMES[idx - 1] + (yr < 10 ? "0" + yr : String(yr));
  }
}

/**
 * Gets the ending balance of the specified previous month sheet.
 */
function getLastMonthEndingBalance(ss, prevSheetName) {
  if (!prevSheetName) return 0;
  var prevSheet = ss.getSheetByName(prevSheetName);
  if (!prevSheet) return 0;

  var lastRow = prevSheet.getLastRow();
  if (lastRow < 2) return 0;

  // Column F is Balance
  var lastBalance = prevSheet.getRange(lastRow, 6).getValue();
  if (typeof lastBalance === "number") {
    return lastBalance;
  }
  var parsed = parseFloat(String(lastBalance).replace(/[^0-9.-]+/g, ""));
  return isNaN(parsed) ? 0 : parsed;
}

/**
 * Checks and inserts Opening Balance row if this is row 2 of a new monthly sheet.
 */
function checkAndInsertOpeningBalance(ss, sheet, sheetName) {
  if (sheet.getLastRow() === 1) {
    var prevSheetName = getPreviousMonthSheetName(sheetName);
    var prevBalance = getLastMonthEndingBalance(ss, prevSheetName);
    if (prevBalance !== 0) {
      var abbr = sheetName.slice(0, 3);
      var yr = parseInt(sheetName.slice(3), 10) + 2000;
      var mIdx = MONTH_NAMES.indexOf(abbr) + 1;
      var openDate = new Date(yr, mIdx - 1, 1);

      sheet.appendRow([
        openDate,
        "Opening Balance",
        "Balance brought forward from " + prevSheetName,
        prevBalance > 0 ? prevBalance : "",
        prevBalance < 0 ? Math.abs(prevBalance) : "",
        '=SUM(D${'$'}2:D2)-SUM(E${'$'}2:E2)',
        'opening-' + sheetName,
        new Date().toISOString()
      ]);

      sheet.getRange(2, 1).setNumberFormat("dd-mm-yyyy");
      sheet.getRange(2, 4, 1, 3).setNumberFormat("${'$'}#,##0.00");
      sheet.hideColumns(7, 2);
      return true;
    }
  }
  return false;
}

/**
 * Gets or creates the monthly sheet with properly formatted headers and hidden metadata columns.
 */
function getOrCreateMonthlySheet(ss, sheetName) {
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);

    // Headers: visible columns (A-F), hidden columns (G-H)
    var headers = ["Date", "Type", "Notes", "Income", "Expense & Bills", "Balance", "ID", "Created At"];
    sheet.appendRow(headers);

    // Header styling
    var headerRange = sheet.getRange("A1:F1");
    headerRange.setFontWeight("bold")
               .setBackground("#1E3A8A")
               .setFontColor("#FFFFFF")
               .setHorizontalAlignment("center");

    sheet.setFrozenRows(1);

    // Column widths for readability
    sheet.setColumnWidth(1, 115); // Date
    sheet.setColumnWidth(2, 135); // Type
    sheet.setColumnWidth(3, 230); // Notes
    sheet.setColumnWidth(4, 130); // Income
    sheet.setColumnWidth(5, 145); // Expense & Bills
    sheet.setColumnWidth(6, 140); // Balance

    // Hide ID and Created At (columns G and H)
    sheet.hideColumns(7, 2);

    // Check and carry over previous month's ending balance
    checkAndInsertOpeningBalance(ss, sheet, sheetName);
  } else {
    // Ensure ID and Created At columns remain hidden
    if (sheet.getMaxColumns() >= 7) {
      sheet.hideColumns(7, 2);
    }
  }
  return sheet;
}

/**
 * Saves business profile information to Sheet1 of the spreadsheet.
 */
function saveBusinessProfileToSheet1(ss, data) {
  var sheet = ss.getSheetByName("Sheet1");
  if (!sheet) {
    sheet = ss.insertSheet("Sheet1", 0);
  } else {
    ss.setActiveSheet(sheet);
    ss.moveActiveSheet(1);
  }

  sheet.clear();

  // Header Banner
  var titleRange = sheet.getRange("A1:B1");
  titleRange.merge();
  titleRange.setValue("BUSINESS PROFILE & REGISTRATION DETAILS");
  titleRange.setFontWeight("bold");
  titleRange.setFontSize(14);
  titleRange.setBackground("#1E3A8A");
  titleRange.setFontColor("#FFFFFF");
  titleRange.setHorizontalAlignment("center");
  sheet.setRowHeight(1, 36);

  var dt = new Date();
  var formattedTimestamp = Utilities.formatDate(dt, Session.getScriptTimeZone() || "GMT", "dd-MM-yyyy a HH:mm");

  var rows = [
    ["1. Business Name", data.businessName || ""],
    ["2. ABN / ACN", data.abnAcn || ""],
    ["3. Business Address", data.businessAddress || ""],
    ["4. Phone / Mobile", data.phoneMobile || ""],
    ["5. Email Address", data.email || ""],
    ["Last Updated", formattedTimestamp]
  ];

  var dataRange = sheet.getRange(2, 1, rows.length, 2);
  dataRange.setValues(rows);
  dataRange.setFontSize(11);

  // Column styling
  var labelRange = sheet.getRange(2, 1, rows.length, 1);
  labelRange.setFontWeight("bold");
  labelRange.setBackground("#F3F4F6");

  sheet.getRange(7, 2).setNumberFormat("@"); // Store timestamp as plain text
  sheet.setColumnWidth(1, 180);
  sheet.setColumnWidth(2, 400);

  return {
    status: "success",
    message: "Business Profile saved to Sheet1 successfully",
    businessName: data.businessName || ""
  };
}

/**
 * Retrieves or initializes the "Products" sheet for POS item suggestions.
 */
function getOrCreateProductsSheet(ss) {
  var sheet = ss.getSheetByName("Products");
  if (!sheet) {
    // Check case-insensitive
    var sheets = ss.getSheets();
    for (var i = 0; i < sheets.length; i++) {
      if (sheets[i].getName().toLowerCase() === "products") {
        return sheets[i];
      }
    }

    // Create new Products sheet
    sheet = ss.insertSheet("Products");
    sheet.appendRow(["Product / Item Name", "Price", "Category"]);

    // Header styling
    var headerRange = sheet.getRange(1, 1, 1, 3);
    headerRange.setBackground("#1E3A8A"); // Deep Blue
    headerRange.setFontColor("#FFFFFF");
    headerRange.setFontWeight("bold");
    sheet.setFrozenRows(1);

    // Pre-populate with standard sample POS items
    var sampleProducts = [
      ["Eyebrows", 10.00, "Threading"],
      ["Uper Lips", 5.00, "Threading"],
      ["Chin", 5.00, "Threading"],
      ["Forehead", 5.00, "Threading"],
      ["Sideburns", 12.00, "Threading"],
      ["Neck", 5.00, "Threading"],
      ["Full Face", 35.00, "Threading"],
      ["Underarms", 15.00, "Body Waxing"],
      ["Full Arms", 30.00, "Body Waxing"],
      ["1/2 Arms", 20.00, "Body Waxing"],
      ["3/4 Arms", 25.00, "Body Waxing"],
      ["Full Legs", 45.00, "Body Waxing"],
      ["1/2 Legs w Knee", 25.00, "Body Waxing"],
      ["1/2 Legs Below Knee", 20.00, "Body Waxing"],
      ["Back", 25.00, "Body Waxing"],
      ["1/2 Back", 20.00, "Body Waxing"],
      ["Stomach", 20.00, "Body Waxing"],
      ["Back of Nick", 15.00, "Body Waxing"],
      ["Sideburns", 15.00, "Facial Waxing"],
      ["Chun", 7.00, "Facial Waxing"],
      ["Upper Lips", 7.00, "Facial Waxing"],
      ["Neck", 7.00, "Facial Waxing"],
      ["Eyebrow", 12.00, "Tinting"],
      ["Eyelash", 20.00, "Tinting"],
      ["EBT+ELT", 30.00, "Tinting"],
      ["Henna (Herbal Henna + Oil)", 30.00, "Tinting"],
      ["Henna (Herbal Henna + Oil)", 25.00, "Tinting"],
      ["20min Clean Up (Cleanse, Scrub, Face Pack)", 20.00, "Herbal Facial (BYO)"],
      ["Hair Oil Massage (10 min)", 15.00, "Hair Care (BYO)"],
      ["Hand Henna -Starting From ${'$'}10", 10.00, "Hair Care (BYO)"]
    ];

    for (var p = 0; p < sampleProducts.length; p++) {
      sheet.appendRow(sampleProducts[p]);
    }

    // Format Price column as Currency (${'$'}#,##0.00)
    sheet.getRange(2, 2, sampleProducts.length, 1).setNumberFormat("${'$'}#,##0.00");
    sheet.autoResizeColumns(1, 3);
  }
  return sheet;
}

/**
 * Reads all products from "Products" sheet.
 */
function getProductsFromSpreadsheet(ss) {
  var sheet = getOrCreateProductsSheet(ss);
  var rows = sheet.getDataRange().getValues();
  var products = [];

  if (rows.length <= 1) return products;

  var header = rows[0];
  var nameIdx = 0;
  var priceIdx = 1;
  var catIdx = 2;

  for (var h = 0; h < header.length; h++) {
    var title = String(header[h]).toLowerCase();
    if (title.indexOf("name") !== -1 || title.indexOf("product") !== -1 || title.indexOf("item") !== -1) {
      nameIdx = h;
    } else if (title.indexOf("price") !== -1 || title.indexOf("amount") !== -1 || title.indexOf("rate") !== -1 || title.indexOf("cost") !== -1) {
      priceIdx = h;
    } else if (title.indexOf("category") !== -1 || title.indexOf("group") !== -1 || title.indexOf("type") !== -1) {
      catIdx = h;
    }
  }

  for (var r = 1; r < rows.length; r++) {
    var row = rows[r];
    if (!row || row.length === 0) continue;

    var rawName = String(row[nameIdx] || "").trim();
    if (!rawName) continue;

    var rawPrice = row[priceIdx];
    var numPrice = 0.0;
    if (typeof rawPrice === "number") {
      numPrice = rawPrice;
    } else if (rawPrice) {
      var cleaned = String(rawPrice).replace(/[^0-9.-]+/g, "");
      numPrice = parseFloat(cleaned) || 0.0;
    }

    var cat = catIdx < row.length ? String(row[catIdx] || "").trim() : "";

    products.push({
      name: rawName,
      price: numPrice,
      category: cat
    });
  }

  return products;
}

/**
 * Adds or updates a product in the "Products" sheet.
 */
function addOrUpdateProductInSpreadsheet(ss, data) {
  var sheet = getOrCreateProductsSheet(ss);
  var name = String(data.name || "").trim();
  if (!name) {
    return { status: "error", message: "Product name is required" };
  }

  var price = parseFloat(data.price) || 0.0;
  var category = String(data.category || "").trim();

  var rows = sheet.getDataRange().getValues();
  var foundRow = -1;

  for (var i = 1; i < rows.length; i++) {
    if (String(rows[i][0]).toLowerCase().trim() === name.toLowerCase()) {
      foundRow = i + 1;
      break;
    }
  }

  if (foundRow > 0) {
    sheet.getRange(foundRow, 2).setValue(price);
    if (category) sheet.getRange(foundRow, 3).setValue(category);
    sheet.getRange(foundRow, 2).setNumberFormat("${'$'}#,##0.00");
    return { status: "success", message: "Updated product " + name };
  } else {
    sheet.appendRow([name, price, category]);
    var newRow = sheet.getLastRow();
    sheet.getRange(newRow, 2).setNumberFormat("${'$'}#,##0.00");
    return { status: "success", message: "Added product " + name };
  }
}

/**
 * Retrieves or initializes the "Appointments" sheet.
 */
function getOrCreateAppointmentsSheet(ss) {
  var sheet = ss.getSheetByName("Appointments");
  if (!sheet) {
    var sheets = ss.getSheets();
    for (var i = 0; i < sheets.length; i++) {
      if (sheets[i].getName().toLowerCase() === "appointments") {
        return sheets[i];
      }
    }

    sheet = ss.insertSheet("Appointments");
    var headers = [
      "Date",
      "Time",
      "Customer Name",
      "Phone",
      "Service",
      "Duration (min)",
      "Price",
      "Status",
      "Notes",
      "ID",
      "Created At"
    ];
    sheet.appendRow(headers);

    var headerRange = sheet.getRange(1, 1, 1, headers.length);
    headerRange.setBackground("#6D28D9");
    headerRange.setFontColor("#FFFFFF");
    headerRange.setFontWeight("bold");
    sheet.setFrozenRows(1);

    sheet.setColumnWidth(1, 110);
    sheet.setColumnWidth(2, 90);
    sheet.setColumnWidth(3, 160);
    sheet.setColumnWidth(4, 130);
    sheet.setColumnWidth(5, 160);
    sheet.setColumnWidth(6, 110);
    sheet.setColumnWidth(7, 100);
    sheet.setColumnWidth(8, 110);
    sheet.setColumnWidth(9, 200);
    sheet.setColumnWidth(10, 90);
    sheet.setColumnWidth(11, 160);

    sheet.hideColumns(10, 2);
  }
  return sheet;
}

/**
 * Reads all appointments from the "Appointments" sheet.
 */
function getAppointmentsFromSpreadsheet(ss) {
  var sheet = getOrCreateAppointmentsSheet(ss);
  var rows = sheet.getDataRange().getValues();
  var appointments = [];
  if (rows.length <= 1) return appointments;

  for (var i = 1; i < rows.length; i++) {
    var row = rows[i];
    if (!row || row.length === 0) continue;

    var rawDate = row[0];
    if (!rawDate) continue;

    var formattedDate = "";
    if (rawDate instanceof Date) {
      formattedDate = Utilities.formatDate(rawDate, Session.getScriptTimeZone(), "yyyy-MM-dd");
    } else {
      var dateStr = String(rawDate).trim();
      var parts = dateStr.split("-");
      if (parts.length === 3 && parts[0].length !== 4) {
        formattedDate = parts[2] + "-" + parts[1] + "-" + parts[0];
      } else {
        formattedDate = dateStr;
      }
    }

    var time = String(row[1] || "");
    var name = String(row[2] || "");
    var phone = String(row[3] || "");
    var service = String(row[4] || "");
    var duration = parseInt(row[5], 10) || 30;
    var price = parseFloat(row[6]) || 0.0;
    var status = String(row[7] || "Scheduled");
    var notes = String(row[8] || "");
    var id = String(row[9] || "");
    var createdAt = String(row[10] || "");

    appointments.push({
      id: id,
      date: formattedDate,
      time: time,
      customerName: name,
      phone: phone,
      service: service,
      duration: duration,
      price: price,
      status: status,
      notes: notes,
      createdAt: createdAt
    });
  }

  return appointments;
}

/**
 * Adds or updates a single appointment in "Appointments" sheet.
 */
function addOrUpdateAppointmentInSpreadsheet(ss, data) {
  var sheet = getOrCreateAppointmentsSheet(ss);
  var id = String(data.id || Utilities.getUuid());
  var rawDateStr = data.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd");
  var date = parseInputDate(rawDateStr);
  var time = String(data.time || "");
  var customerName = String(data.customerName || "");
  var phone = String(data.phone || "");
  var service = String(data.service || "");
  var duration = parseInt(data.duration, 10) || 30;
  var price = parseFloat(data.price) || 0.0;
  var status = String(data.status || "Scheduled");
  var notes = String(data.notes || "");
  var createdAt = data.createdAt ? String(data.createdAt) : new Date().toISOString();

  var rows = sheet.getDataRange().getValues();
  var foundRow = -1;

  for (var i = 1; i < rows.length; i++) {
    var rowId = String(rows[i][9] || "");
    var rowPhone = String(rows[i][3] || "");
    var rowTime = String(rows[i][1] || "");
    var rDate = rows[i][0] instanceof Date ? Utilities.formatDate(rows[i][0], Session.getScriptTimeZone(), "yyyy-MM-dd") : String(rows[i][0]);

    if (id && rowId && (rowId === id || id.indexOf(rowId) !== -1 || rowId.indexOf(id) !== -1)) {
      foundRow = i + 1;
      break;
    } else if (!id && phone && rowPhone === phone && time === rowTime && rDate === rawDateStr) {
      foundRow = i + 1;
      break;
    }
  }

  if (foundRow > 0) {
    sheet.getRange(foundRow, 1, 1, 11).setValues([[
      date, time, customerName, phone, service, duration, price, status, notes, id, createdAt
    ]]);
  } else {
    sheet.appendRow([
      date, time, customerName, phone, service, duration, price, status, notes, id, createdAt
    ]);
  }

  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    sheet.getRange(2, 1, lastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
    sheet.getRange(2, 7, lastRow - 1, 1).setNumberFormat("${'$'}#,##0.00");
    if (lastRow > 2) {
      sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).sort([
        {column: 1, ascending: true},
        {column: 2, ascending: true}
      ]);
    }
  }

  return {
    status: "success",
    message: foundRow > 0 ? "Appointment updated" : "Appointment added",
    id: id
  };
}

/**
 * Batch saves appointments to the "Appointments" sheet.
 */
function batchSaveAppointmentsInSpreadsheet(ss, data) {
  var appts = data.appointments || [];
  if (!appts || appts.length === 0) {
    return { status: "success", count: 0, message: "No appointments provided" };
  }

  var sheet = getOrCreateAppointmentsSheet(ss);
  var rows = sheet.getDataRange().getValues();
  var idToRowMap = {};

  for (var i = 1; i < rows.length; i++) {
    var rId = String(rows[i][9] || "");
    if (rId) {
      idToRowMap[rId] = i + 1;
    }
  }

  for (var a = 0; a < appts.length; a++) {
    var item = appts[a];
    var id = String(item.id || Utilities.getUuid());
    var rawDateStr = item.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd");
    var date = parseInputDate(rawDateStr);
    var time = String(item.time || "");
    var customerName = String(item.customerName || "");
    var phone = String(item.phone || "");
    var service = String(item.service || "");
    var duration = parseInt(item.duration, 10) || 30;
    var price = parseFloat(item.price) || 0.0;
    var status = String(item.status || "Scheduled");
    var notes = String(item.notes || "");
    var createdAt = item.createdAt ? String(item.createdAt) : new Date().toISOString();

    var existingRow = idToRowMap[id];
    if (existingRow) {
      sheet.getRange(existingRow, 1, 1, 11).setValues([[
        date, time, customerName, phone, service, duration, price, status, notes, id, createdAt
      ]]);
    } else {
      sheet.appendRow([
        date, time, customerName, phone, service, duration, price, status, notes, id, createdAt
      ]);
      idToRowMap[id] = sheet.getLastRow();
    }
  }

  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    sheet.getRange(2, 1, lastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
    sheet.getRange(2, 7, lastRow - 1, 1).setNumberFormat("${'$'}#,##0.00");
    if (lastRow > 2) {
      sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).sort([
        {column: 1, ascending: true},
        {column: 2, ascending: true}
      ]);
    }
  }

  return {
    status: "success",
    count: appts.length,
    message: "Saved " + appts.length + " appointments"
  };
}

/**
 * Deletes an appointment from the "Appointments" sheet by ID.
 */
function deleteAppointmentFromSpreadsheet(ss, data) {
  var id = String(data.id || "");
  if (!id) return { status: "error", message: "ID is required" };

  var sheet = getOrCreateAppointmentsSheet(ss);
  var rows = sheet.getDataRange().getValues();
  for (var i = 1; i < rows.length; i++) {
    var rId = String(rows[i][9] || "");
    if (rId && (rId === id || id.indexOf(rId) !== -1 || rId.indexOf(id) !== -1)) {
      sheet.deleteRow(i + 1);
      return { status: "success", message: "Appointment deleted" };
    }
  }

  return { status: "not_found", message: "Appointment not found to delete" };
}

/**
 * Batch adds transactions to monthly sheets.
 */
function batchAddTransactions(ss, data) {
  var txs = data.transactions || [];
  if (!txs || txs.length === 0) {
    return { status: "success", count: 0, message: "No transactions to add" };
  }

  for (var t = 0; t < txs.length; t++) {
    var item = txs[t];
    var id = item.id || Utilities.getUuid();
    var rawDateStr = item.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd");
    var date = parseInputDate(rawDateStr);
    var type = item.type || "Daily Income";
    var notes = item.notes || item.category || "";
    var amount = parseFloat(item.amount) || 0.0;
    var createdAt = item.timestamp ? new Date(item.timestamp).toISOString() : new Date().toISOString();

    var sheetName = getMonthSheetName(date);
    var sheet = getOrCreateMonthlySheet(ss, sheetName);
    checkAndInsertOpeningBalance(ss, sheet, sheetName);

    var isIncome = (type === "Daily Income" || type === "Opening Balance");
    var incomeValue = isIncome ? amount : "";
    var expenseValue = !isIncome ? amount : "";

    sheet.appendRow([
      date,
      type,
      notes,
      incomeValue,
      expenseValue,
      "",
      id,
      createdAt
    ]);

    var lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      sheet.getRange(2, 1, lastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
      if (lastRow > 2) {
        sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).sort([{column: 1, ascending: true}]);
      }
      var dataRows = sheet.getLastRow() - 1;
      if (dataRows > 0) {
        sheet.getRange(2, 6, dataRows, 1).setFormulaR1C1("=SUM(R2C4:RC4)-SUM(R2C5:RC5)");
        sheet.getRange(2, 4, dataRows, 3).setNumberFormat("${'$'}#,##0.00");
      }
    }
  }

  return {
    status: "success",
    count: txs.length,
    message: "Batch added " + txs.length + " transactions"
  };
}

/**
 * Updates an existing transaction's amount, notes, or type in the monthly sheets.
 */
function updateTransactionInSpreadsheet(ss, data) {
  var id = data.id ? String(data.id) : "";
  var date = data.date ? String(data.date) : "";
  var type = data.type ? String(data.type) : "";
  var newAmount = parseFloat(data.amount) || 0.0;
  var notes = data.notes || data.category;
  
  var targetSheet = null;
  var targetSheetName = "";

  if (date) {
    targetSheetName = getMonthSheetName(date);
    targetSheet = ss.getSheetByName(targetSheetName);
  }

  var foundRow = -1;
  var sheetToUpdate = null;

  // 1. Search in target sheet first if available
  if (targetSheet) {
    var lastRow = targetSheet.getLastRow();
    if (lastRow >= 2) {
      var values = targetSheet.getRange(2, 1, lastRow - 1, 8).getValues();
      for (var i = 0; i < values.length; i++) {
        var rowId = String(values[i][6] || "");
        var rowDate = values[i][0] instanceof Date ? Utilities.formatDate(values[i][0], Session.getScriptTimeZone(), "yyyy-MM-dd") : String(values[i][0]);
        var rowType = String(values[i][1] || "");
        
        if (id && rowId && (rowId === id || id.indexOf(rowId) !== -1 || rowId.indexOf(id) !== -1)) {
          foundRow = i + 2;
          sheetToUpdate = targetSheet;
          break;
        } else if (!id && date && type && rowDate === date && rowType === type) {
          foundRow = i + 2;
          sheetToUpdate = targetSheet;
          break;
        }
      }
    }
  }

  // 2. Search across all monthly sheets if not found yet
  if (foundRow === -1) {
    var allSheets = ss.getSheets();
    for (var s = 0; s < allSheets.length; s++) {
      var curSheet = allSheets[s];
      if (curSheet.getName() === "Sheet1" || curSheet.getName() === "Products") continue;
      var curLastRow = curSheet.getLastRow();
      if (curLastRow < 2) continue;
      var curValues = curSheet.getRange(2, 1, curLastRow - 1, 8).getValues();
      for (var j = 0; j < curValues.length; j++) {
        var cRowId = String(curValues[j][6] || "");
        var cRowDate = curValues[j][0] instanceof Date ? Utilities.formatDate(curValues[j][0], Session.getScriptTimeZone(), "yyyy-MM-dd") : String(curValues[j][0]);
        var cRowType = String(curValues[j][1] || "");

        if (id && cRowId && (cRowId === id || id.indexOf(cRowId) !== -1 || cRowId.indexOf(id) !== -1)) {
          foundRow = j + 2;
          sheetToUpdate = curSheet;
          break;
        } else if (date && type && cRowDate === date && cRowType === type) {
          foundRow = j + 2;
          sheetToUpdate = curSheet;
          break;
        }
      }
      if (foundRow !== -1) break;
    }
  }

  if (sheetToUpdate && foundRow !== -1) {
    var isIncome = (type === "Daily Income" || type === "Opening Balance");
    if (!type) {
      var existingType = sheetToUpdate.getRange(foundRow, 2).getValue();
      isIncome = (existingType === "Daily Income" || existingType === "Opening Balance");
    }

    if (isIncome) {
      sheetToUpdate.getRange(foundRow, 4).setValue(newAmount);
      sheetToUpdate.getRange(foundRow, 5).setValue("");
    } else {
      sheetToUpdate.getRange(foundRow, 4).setValue("");
      sheetToUpdate.getRange(foundRow, 5).setValue(newAmount);
    }

    if (notes !== undefined && notes !== null) {
      sheetToUpdate.getRange(foundRow, 3).setValue(notes);
    }

    // Refresh balance formulas and sort
    var finalLastRow = sheetToUpdate.getLastRow();
    if (finalLastRow > 1) {
      sheetToUpdate.getRange(2, 1, finalLastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
      sheetToUpdate.getRange(2, 6, finalLastRow - 1, 1).setFormulaR1C1("=SUM(R2C4:RC4)-SUM(R2C5:RC5)");
      sheetToUpdate.getRange(2, 4, finalLastRow - 1, 3).setNumberFormat("${'$'}#,##0.00");
    }

    return {
      status: "success",
      message: "Transaction amount updated to " + newAmount,
      updatedRow: foundRow,
      sheet: sheetToUpdate.getName()
    };
  } else {
    // If not found, insert as a new transaction in the proper month
    var appendTarget = targetSheet || getOrCreateMonthlySheet(ss, targetSheetName || getMonthSheetName(new Date()));
    var parsedD = parseInputDate(date);
    
    var isInc = (type === "Daily Income" || type === "Opening Balance");
    appendTarget.appendRow([
      parsedD,
      type || "Daily Income",
      notes || "",
      isInc ? newAmount : "",
      !isInc ? newAmount : "",
      "",
      id || Utilities.getUuid(),
      new Date().toISOString()
    ]);

    var aLastRow = appendTarget.getLastRow();
    if (aLastRow > 1) {
      appendTarget.getRange(2, 1, aLastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
      if (aLastRow > 2) {
        appendTarget.getRange(2, 1, aLastRow - 1, appendTarget.getLastColumn()).sort([{column: 1, ascending: true}]);
      }
      appendTarget.getRange(2, 6, aLastRow - 1, 1).setFormulaR1C1("=SUM(R2C4:RC4)-SUM(R2C5:RC5)");
      appendTarget.getRange(2, 4, aLastRow - 1, 3).setNumberFormat("${'$'}#,##0.00");
    }

    return {
      status: "success",
      message: "Inserted as new transaction and balance recalculated",
      sheet: appendTarget.getName()
    };
  }
}

/**
 * Robust date parser supporting YYYY-MM-DD and DD-MM-YYYY.
 */
function parseInputDate(rawDateStr) {
  if (!rawDateStr) return new Date();
  var dateParts = String(rawDateStr).trim().split("-");
  if (dateParts.length === 3) {
    if (dateParts[0].length === 4) {
      // YYYY-MM-DD
      return new Date(parseInt(dateParts[0], 10), parseInt(dateParts[1], 10) - 1, parseInt(dateParts[2], 10));
    } else {
      // DD-MM-YYYY
      return new Date(parseInt(dateParts[2], 10), parseInt(dateParts[1], 10) - 1, parseInt(dateParts[0], 10));
    }
  }
  return new Date();
}

/**
 * Deletes a transaction from the spreadsheet and recalculates running balance formulas.
 */
function deleteTransactionFromSpreadsheet(ss, data) {
  var id = data.id ? String(data.id) : "";
  var date = data.date ? String(data.date) : "";
  var type = data.type ? String(data.type) : "";

  var sheets = ss.getSheets();
  var foundRow = -1;
  var sheetToModify = null;

  for (var s = 0; s < sheets.length; s++) {
    var curSheet = sheets[s];
    if (curSheet.getName() === "Sheet1" || curSheet.getName() === "Products") continue;
    var lastRow = curSheet.getLastRow();
    if (lastRow < 2) continue;

    var rows = curSheet.getRange(2, 1, lastRow - 1, 8).getValues();
    for (var j = 0; j < rows.length; j++) {
      var rId = String(rows[j][6] || "");
      var rDate = rows[j][0] instanceof Date ? Utilities.formatDate(rows[j][0], Session.getScriptTimeZone(), "yyyy-MM-dd") : String(rows[j][0]);
      var rType = String(rows[j][1] || "");

      if (id && rId && (rId === id || id.indexOf(rId) !== -1 || rId.indexOf(id) !== -1)) {
        foundRow = j + 2;
        sheetToModify = curSheet;
        break;
      } else if (!id && date && type && rDate === date && rType === type) {
        foundRow = j + 2;
        sheetToModify = curSheet;
        break;
      }
    }
    if (foundRow !== -1) break;
  }

  if (foundRow !== -1 && sheetToModify) {
    sheetToModify.deleteRow(foundRow);

    // Refresh balance formulas for ALL rows in this sheet to ensure consistency
    var newLastRow = sheetToModify.getLastRow();
    if (newLastRow > 1) {
      sheetToModify.getRange(2, 1, newLastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
      sheetToModify.getRange(2, 6, newLastRow - 1, 1).setFormulaR1C1("=SUM(R2C4:RC4)-SUM(R2C5:RC5)");
      sheetToModify.getRange(2, 4, newLastRow - 1, 3).setNumberFormat("${'$'}#,##0.00");
    }

    return {
      status: "success",
      message: "Transaction deleted and balance recalculated from " + sheetToModify.getName(),
      sheet: sheetToModify.getName()
    };
  } else {
    return {
      status: "not_found",
      message: "Transaction not found to delete"
    };
  }
}

/**
 * Handles incoming POST requests from the Android App.
 */
function doPost(e) {
  try {
    var contents = e.postData ? e.postData.contents : "";
    if (!contents) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        message: "Empty payload"
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var data = JSON.parse(contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    // Check if this is a request to save Business Profile information to Sheet1
    if (data.action === "save_profile" || (data.businessName !== undefined && data.type === undefined)) {
      var profileResult = saveBusinessProfileToSheet1(ss, data);
      return ContentService.createTextOutput(JSON.stringify(profileResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a request to save an Appointment
    if (data.action === "save_appointment" || data.action === "add_appointment") {
      var apptResult = addOrUpdateAppointmentInSpreadsheet(ss, data);
      return ContentService.createTextOutput(JSON.stringify(apptResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a batch appointments save request
    if (data.action === "save_appointments_batch") {
      var batchApptResult = batchSaveAppointmentsInSpreadsheet(ss, data);
      return ContentService.createTextOutput(JSON.stringify(batchApptResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a request to delete an appointment
    if (data.action === "delete_appointment") {
      var delApptResult = deleteAppointmentFromSpreadsheet(ss, data);
      return ContentService.createTextOutput(JSON.stringify(delApptResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a batch transactions save request
    if (data.action === "batch_add" || data.action === "sync_transactions") {
      var batchTxResult = batchAddTransactions(ss, data);
      return ContentService.createTextOutput(JSON.stringify(batchTxResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a request to save a Product item
    if (data.action === "save_product" || data.action === "add_product") {
      var prodResult = addOrUpdateProductInSpreadsheet(ss, data);
      return ContentService.createTextOutput(JSON.stringify(prodResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a request to update an existing transaction amount
    if (data.action === "update_transaction" || data.action === "edit_transaction") {
      var updateResult = updateTransactionInSpreadsheet(ss, data);
      return ContentService.createTextOutput(JSON.stringify(updateResult)).setMimeType(ContentService.MimeType.JSON);
    }

    // Check if this is a request to delete an existing transaction
    if (data.action === "delete_transaction") {
      var deleteResult = deleteTransactionFromSpreadsheet(ss, data);
      return ContentService.createTextOutput(JSON.stringify(deleteResult)).setMimeType(ContentService.MimeType.JSON);
    }

    var id = data.id || Utilities.getUuid();
    var rawDateStr = data.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd");
    var date = parseInputDate(rawDateStr);
    var type = data.type || "Daily Income";
    var notes = data.notes || data.category || "";
    var amount = parseFloat(data.amount) || 0.0;
    var createdAt = new Date().toISOString();

    // Determine target monthly sheet (e.g. Sep26, Oct26)
    var sheetName = getMonthSheetName(date);
    var sheet = getOrCreateMonthlySheet(ss, sheetName);

    // Ensure opening balance row exists if this is the first transaction of the month
    checkAndInsertOpeningBalance(ss, sheet, sheetName);

    // Income vs Expense & Bills separation
    var isIncome = (type === "Daily Income" || type === "Opening Balance");
    var incomeValue = isIncome ? amount : "";
    var expenseValue = !isIncome ? amount : "";

    sheet.appendRow([
      date,
      type,
      notes,
      incomeValue,
      expenseValue,
      "", // Balance will be recalculated after sort
      id,
      createdAt
    ]);

    var lastRow = sheet.getLastRow();
    
    // Format Date column (A)
    if (lastRow > 1) {
      sheet.getRange(2, 1, lastRow - 1, 1).setNumberFormat("dd-mm-yyyy");
    }
    
    // Sort by Date (Column A) ascending
    if (lastRow > 2) {
      sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).sort([{column: 1, ascending: true}]);
    }

    // Recalculate Balance formulas for all rows to ensure accuracy after sorting
    var dataRows = sheet.getLastRow() - 1;
    if (dataRows > 0) {
      var balanceRange = sheet.getRange(2, 6, dataRows, 1);
      balanceRange.setFormulaR1C1("=SUM(R2C4:RC4)-SUM(R2C5:RC5)");
      
      // Format Income, Expense & Bills, and Balance as Currency (${'$'}#,##0.00)
      sheet.getRange(2, 4, dataRows, 3).setNumberFormat("${'$'}#,##0.00");
    }

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      message: "Transaction added, sorted, and balance recalculated in " + sheetName,
      monthSheet: sheetName,
      row: lastRow
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: error.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * Handles GET requests:
 * 1. action=get_products: Returns list of products with name and price from "Products" sheet.
 * 2. action=pdf: Returns complete spreadsheet PDF export URL.
 * 3. action=get_profile: Returns Business Profile from Sheet1.
 * 4. Default: Retrieves transactions across monthly sheets.
 */
function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    // Check for Appointments retrieval request
    if (e && e.parameter && (e.parameter.action === "get_appointments" || e.parameter.action === "appointments")) {
      var apptList = getAppointmentsFromSpreadsheet(ss);
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        count: apptList.length,
        appointments: apptList
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // Check for Products retrieval request (POS Point of Sale)
    if (e && e.parameter && (e.parameter.action === "get_products" || e.parameter.action === "products")) {
      var productList = getProductsFromSpreadsheet(ss);
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        count: productList.length,
        products: productList
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // Check for PDF export request
    if (e && e.parameter && (e.parameter.action === "pdf" || e.parameter.format === "pdf")) {
      var ssUrl = ss.getUrl();
      var exportUrl = ssUrl.replace(/\/edit.*${'$'}/, '') + '/export?format=pdf&size=letter&portrait=true&fitw=true&gridlines=true';
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        pdfUrl: exportUrl,
        spreadsheetUrl: ssUrl,
        title: ss.getName()
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // Check for profile retrieval request
    if (e && e.parameter && e.parameter.action === "get_profile") {
      var profileSheet = ss.getSheetByName("Sheet1");
      var profileData = {};
      if (profileSheet && profileSheet.getLastRow() >= 6) {
        var vals = profileSheet.getRange(2, 1, 5, 2).getValues();
        profileData.businessName = vals[0][1] || "";
        profileData.abnAcn = vals[1][1] || "";
        profileData.businessAddress = vals[2][1] || "";
        profileData.phoneMobile = vals[3][1] || "";
        profileData.email = vals[4][1] || "";
      }
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        profile: profileData
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var sheets = ss.getSheets();
    var result = [];

    // Optional query param: ?month=Sep26
    var filterMonth = (e && e.parameter && e.parameter.month) ? e.parameter.month.trim() : null;

    for (var s = 0; s < sheets.length; s++) {
      var sheet = sheets[s];
      var name = sheet.getName();

      // Skip non-transaction sheets
      if (name === "Sheet1" || name === "Products" || name === "Appointments") {
        continue;
      }

      var isMonthSheet = /^[A-Z][a-z]{2}\d{2}${'$'}/.test(name);
      var isLegacySheet = (name === "Transactions");

      if (!isMonthSheet && !isLegacySheet) {
        continue;
      }

      if (filterMonth && name !== filterMonth) {
        continue;
      }

      var rows = sheet.getDataRange().getValues();
      if (rows.length <= 1) continue;

      var headerRow = rows[0];
      var isNewFormat = (headerRow.length >= 6 && headerRow[3] === "Income");

      for (var i = 1; i < rows.length; i++) {
        var row = rows[i];
        if (!row || row.length === 0) continue;

        if (isNewFormat) {
          // New format: [Date, Type, Notes, Income, Expense & Bills, Balance, ID, Created At]
          var rawDate = row[0];
          if (!rawDate) continue;

          var formattedDate = "";
          if (rawDate instanceof Date) {
            formattedDate = Utilities.formatDate(rawDate, Session.getScriptTimeZone(), "yyyy-MM-dd");
          } else {
            var dateStr = String(rawDate);
            var parts = dateStr.trim().split("-");
            if (parts.length === 3 && parts[0].length !== 4) {
              formattedDate = parts[2] + "-" + parts[1] + "-" + parts[0];
            } else {
              formattedDate = dateStr;
            }
          }

          var type = String(row[1] || "Daily Income");
          var notes = String(row[2] || "");
          var income = parseFloat(row[3]) || 0.0;
          var expense = parseFloat(row[4]) || 0.0;
          var balance = parseFloat(row[5]) || 0.0;
          var id = String(row[6] || "");
          var createdAt = String(row[7] || "");
          var amount = income > 0 ? income : expense;

          result.push({
            id: id,
            date: formattedDate,
            type: type,
            notes: notes,
            income: income,
            expense: expense,
            amount: amount,
            balance: balance,
            monthSheet: name,
            createdAt: createdAt
          });
        } else {
          // Legacy format: [ID, Date, Type, Notes, Amount, Created At]
          var legacyDate = row[1];
          if (!legacyDate) continue;

          var fDate = "";
          if (legacyDate instanceof Date) {
            fDate = Utilities.formatDate(legacyDate, Session.getScriptTimeZone(), "yyyy-MM-dd");
          } else {
            var lDateStr = String(legacyDate);
            var lParts = lDateStr.trim().split("-");
            if (lParts.length === 3 && lParts[0].length !== 4) {
              fDate = lParts[2] + "-" + lParts[1] + "-" + lParts[0];
            } else {
              fDate = lDateStr;
            }
          }

          var legType = String(row[2] || "Daily Income");
          var legNotes = String(row[3] || "");
          var legAmount = parseFloat(row[4]) || 0.0;

          result.push({
            id: String(row[0] || ""),
            date: fDate,
            type: legType,
            notes: legNotes,
            income: legType === "Daily Income" ? legAmount : 0.0,
            expense: legType !== "Daily Income" ? legAmount : 0.0,
            amount: legAmount,
            monthSheet: name,
            createdAt: String(row[5] || "")
          });
        }
      }
    }

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      count: result.length,
      products_endpoint: "?action=get_products",
      data: result
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: error.toString(),
      data: []
    })).setMimeType(ContentService.MimeType.JSON);
  }
}
""".trimIndent()
}
