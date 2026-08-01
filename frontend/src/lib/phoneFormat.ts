/**
 * Strips the formatting characters that show up when a phone number is
 * pasted from Contacts, a text message, an email signature, etc. - spaces,
 * hyphens, parentheses, dots - while leaving a leading "+" (country code
 * prefix) and the digits untouched.
 *
 * "+91 98765 - 43210"  -> "+919876543210"
 * "(987) 654-3210"     -> "9876543210"
 * "987.654.3210"       -> "9876543210"
 */
export function normalizePhoneNumber(raw: string): string {
  const trimmed = raw.trim();
  const hasLeadingPlus = trimmed.startsWith("+");
  const digitsOnly = trimmed.replace(/[^0-9]/g, "");
  return hasLeadingPlus ? `+${digitsOnly}` : digitsOnly;
}
