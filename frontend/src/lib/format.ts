/**
 * Deterministic formatters for server-rendered pages. The Node.js server and the
 * visitor's browser can have different default locales and time zones, and any
 * difference in rendered text is a hydration mismatch. These helpers pin both.
 *
 * Values that genuinely depend on the visitor's clock or time zone (for example a
 * "sale ends at 5:00 PM" label) should render only after hydration instead.
 */
const LOCALE = "en-US";

export function formatCount(n: number): string {
  return n.toLocaleString(LOCALE);
}

const CALENDAR_DATE: Intl.DateTimeFormatOptions = {
  month: "short",
  day: "numeric",
  year: "numeric",
  timeZone: "UTC",
};

/**
 * Formats a calendar date such as "Dec 1, 2026". Date-only API values (`2026-12-01`)
 * parse as UTC midnight, so formatting in UTC also keeps them from shifting a day
 * for visitors west of UTC.
 */
export function formatCalendarDate(iso: string): string {
  return new Date(iso).toLocaleDateString(LOCALE, CALENDAR_DATE);
}

const moneyFormatters = new Map<string, Intl.NumberFormat>();

/**
 * Formats an amount in major units ("19.99") for display, e.g. "$19.99" or
 * "CA$19.99". Falls back to "19.99 XYZ" for a code Intl doesn't recognise.
 */
export function formatMoney(amount: number, currency = "USD"): string {
  const code = currency.toUpperCase();
  let formatter = moneyFormatters.get(code);
  if (!formatter) {
    try {
      formatter = new Intl.NumberFormat(LOCALE, {
        style: "currency",
        currency: code,
      });
    } catch {
      return `${amount.toFixed(2)} ${code}`;
    }
    moneyFormatters.set(code, formatter);
  }
  return formatter.format(amount);
}
