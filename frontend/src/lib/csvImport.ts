import Papa from "papaparse";
import { ImportRow, ParsedCsv } from "@/types/api";

const KNOWN_COLUMNS = ["Date", "Description", "Category", "Cost", "Currency"];

export class CsvParseError extends Error {}

/**
 * Parses a Splitwise CSV export. Format: Date,Description,Category,Cost,Currency,<Member1>,<Member2>,...
 * Each member column holds their NET value for that row (positive = they fronted money,
 * negative = they owe their share). The final "Total balance" summary row (blank cost) is
 * dropped automatically, as are any other rows with an unparsable cost.
 */
export function parseSplitwiseCsv(csvText: string): ParsedCsv {
  const result = Papa.parse<Record<string, string>>(csvText, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (h) => h.trim(),
  });

  if (result.errors.length > 0 && (!result.data || result.data.length === 0)) {
    throw new CsvParseError(
      "Could not parse this file - make sure it's a Splitwise expense export CSV.",
    );
  }

  const headers = result.meta.fields ?? [];
  const members = headers.filter(
    (h) => !KNOWN_COLUMNS.includes(h) && h.trim().length > 0,
  );

  if (members.length === 0) {
    throw new CsvParseError(
      "No member columns found. Expected columns like Date, Description, Category, Cost, Currency, followed by one column per group member.",
    );
  }

  const rows: ImportRow[] = [];

  for (const record of result.data) {
    const description = (record["Description"] ?? "").trim();
    const costRaw = (record["Cost"] ?? "").trim();

    // Skip the trailing "Total balance" summary row and any other row with a missing/invalid cost.
    if (!costRaw || description.toLowerCase() === "total balance") continue;

    const cost = parseFloat(costRaw);
    if (isNaN(cost)) continue;

    const date = (record["Date"] ?? "").trim();
    if (!date) continue;

    const memberValues: Record<string, number> = {};
    for (const member of members) {
      const raw = (record[member] ?? "").trim();
      const value = raw ? parseFloat(raw) : 0;
      if (!isNaN(value)) memberValues[member] = value;
    }

    rows.push({
      date,
      description,
      category: (record["Category"] ?? "").trim(),
      cost,
      currency: (record["Currency"] ?? "INR").trim() || "INR",
      memberValues,
    });
  }

  if (rows.length === 0) {
    throw new CsvParseError("No expense rows found in this file.");
  }

  return { members, rows };
}

export function summarizeParsedCsv(parsed: ParsedCsv) {
  const expenseRows = parsed.rows.filter(
    (r) => r.category.toLowerCase() !== "payment",
  );
  const paymentRows = parsed.rows.filter(
    (r) => r.category.toLowerCase() === "payment",
  );
  const totalAmount = expenseRows.reduce((sum, r) => sum + r.cost, 0);
  const dates = parsed.rows.map((r) => r.date).sort();

  return {
    expenseCount: expenseRows.length,
    paymentCount: paymentRows.length,
    totalAmount,
    dateFrom: dates[0],
    dateTo: dates[dates.length - 1],
  };
}
