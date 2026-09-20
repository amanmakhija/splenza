import { Linking } from "react-native";

/**
 * Builds a standard UPI "collect/pay" deep link per the NPCI UPI Linking
 * Specification. Opening this hands off to whichever UPI app the user
 * chooses (GPay, PhonePe, Paytm, etc.) - we have no way to know from inside
 * this app whether the payment that follows actually succeeds. See
 * `useUpiPayment` for how callers should handle the return flow.
 */
export function buildUpiUri(params: {
  payeeVpa: string;
  payeeName: string;
  amount: number;
  note?: string;
}): string {
  const { payeeVpa, payeeName, amount, note } = params;
  const query = new URLSearchParams({
    pa: payeeVpa,
    pn: payeeName,
    am: amount.toFixed(2),
    cu: "INR",
  });
  if (note && note.trim()) {
    query.set("tn", note.trim());
  }
  return `upi://pay?${query.toString()}`;
}

/**
 * Attempts to open a UPI app with the given payment details prefilled.
 * Returns "opened" | "no-upi-app" | "error" so the caller can decide what
 * to tell the user - we never throw here.
 */
export async function openUpiPayment(params: {
  payeeVpa: string;
  payeeName: string;
  amount: number;
  note?: string;
}): Promise<"opened" | "no-upi-app" | "error"> {
  const uri = buildUpiUri(params);
  try {
    const canOpen = await Linking.canOpenURL(uri);
    if (!canOpen) return "no-upi-app";
    await Linking.openURL(uri);
    return "opened";
  } catch {
    return "error";
  }
}
