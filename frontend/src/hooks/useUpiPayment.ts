import { useCallback, useRef } from "react";
import { AppState, AppStateStatus } from "react-native";
import { alert } from "@/components/AppAlert";
import { openUpiPayment } from "@/lib/upi";

export interface UpiPaymentParams {
  payeeVpa: string;
  payeeName: string;
  amount: number;
  note?: string;
}

/**
 * Launches a UPI app with the payment prefilled, then - once the user comes
 * back to Splenza - asks whether the payment actually went through.
 *
 * We have no way to verify a UPI payment's outcome from inside the app (it
 * completes entirely inside GPay/PhonePe/etc.), so this never auto-records
 * anything. It only ever prompts; the user's "Yes" is what triggers
 * `onConfirm`, which callers use to open the settlement form prefilled -
 * saving it is still a separate, explicit step there.
 */
export function useUpiPayment() {
  const pendingRef = useRef<{
    params: UpiPaymentParams;
    onConfirm: () => void;
  } | null>(null);
  const subRef = useRef<{ remove: () => void } | null>(null);
  const appStateRef = useRef<AppStateStatus>(AppState.currentState);

  const cleanupListener = useCallback(() => {
    subRef.current?.remove();
    subRef.current = null;
  }, []);

  const handleAppStateChange = useCallback(
    (next: AppStateStatus) => {
      const prev = appStateRef.current;
      appStateRef.current = next;
      const pending = pendingRef.current;
      if (!pending) return;
      // Only fire once we've actually left and come back (background/inactive
      // -> active). A same-state event or one that fires before backgrounding
      // shouldn't trigger the prompt.
      if (prev !== "active" && next === "active") {
        pendingRef.current = null;
        cleanupListener();
        const { params, onConfirm } = pending;
        alert(
          "Did you complete this payment?",
          `Confirm you paid ₹${params.amount.toFixed(2)} to ${params.payeeName} via UPI.`,
          [
            { text: "No, I didn't pay", style: "cancel" },
            { text: "Yes, I paid", onPress: onConfirm },
          ],
        );
      }
    },
    [cleanupListener],
  );

  const payViaUpi = useCallback(
    async (params: UpiPaymentParams, onConfirm: () => void) => {
      const result = await openUpiPayment(params);
      if (result === "no-upi-app") {
        alert(
          "No UPI app found",
          "Install a UPI app like Google Pay or PhonePe to pay directly, or record this as a manual settlement instead.",
        );
        return;
      }
      if (result === "error") {
        alert(
          "Couldn't open UPI app",
          "Something went wrong launching your UPI app. You can still record a manual settlement.",
        );
        return;
      }
      // "opened": wait for the user to return to Splenza, then prompt.
      pendingRef.current = { params, onConfirm };
      cleanupListener();
      subRef.current = AppState.addEventListener(
        "change",
        handleAppStateChange,
      );
    },
    [cleanupListener, handleAppStateChange],
  );

  return { payViaUpi };
}
