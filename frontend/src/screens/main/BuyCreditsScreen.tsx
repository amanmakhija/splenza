import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ActivityIndicator,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, Sparkles, Check, ShieldCheck } from "lucide-react-native";
import * as RNIap from "react-native-iap";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { alert } from "@/components/AppAlert";
import { Button } from "@/components/Button";
import { useAiCredits, useInvalidateAiCredits } from "@/hooks/useAiCredits";
import { CreditPackage } from "@/types/api";

// Derived structurally from the library's actual function signatures rather
// than importing `RNIap.ProductPurchase`/`RNIap.Product` by name - those
// named types aren't reliably resolvable across react-native-iap versions
// when TypeScript follows the package's `"react-native"` source-file export
// path instead of its compiled `.d.ts` (this bit us with a "has no exported
// member" error). Deriving from the function signatures works regardless of
// which named types the package chooses to export.
type PlayPurchase = Parameters<
  Parameters<typeof RNIap.purchaseUpdatedListener>[0]
>[0];
type PlayProduct = Awaited<ReturnType<typeof RNIap.getProducts>>[number];

async function fetchPackages({
  signal,
}: {
  signal: AbortSignal;
}): Promise<CreditPackage[]> {
  const { data } = await apiClient.get<CreditPackage[]>(
    "/api/v1/ai-credits/packages",
    { signal },
  );
  return data;
}

/** Sends the finished Google Play purchase to the backend to verify against the
 * Play Developer API and credit the shared AI wallet. Only after this succeeds
 * do we consume the purchase on-device (see `finishTransaction` below) - if we
 * consumed it first and the app died before this request landed, the user would
 * have paid without ever getting credits, with no way to retry. */
async function verifyPurchase(purchase: PlayPurchase) {
  await apiClient.post("/api/v1/ai-credits/purchases/verify", {
    productId: purchase.productId,
    purchaseToken: purchase.purchaseToken,
    // Included for completeness/debugging - purchaseToken is what the backend
    // actually verifies against the Play Developer API.
    transactionReceipt: purchase.transactionReceipt,
  });
}

/**
 * Lets the user top up their shared AI-credit wallet (spendable on receipt
 * scanning, voice expense entry, and any future AI feature) via Google Play
 * Billing. Packages are managed (consumable) in-app products in Play
 * Console; `CreditPackage.googlePlayProductId` maps each one to its SKU.
 *
 * Flow: fetch products from Play -> user picks one -> requestPurchase ->
 * purchaseUpdatedListener fires -> send the purchase to our backend to
 * verify server-side against the Play Developer API and grant credits ->
 * only then finishTransaction (consumable) so the item becomes
 * re-purchasable. If the app is killed mid-flow, `getAvailablePurchases` on
 * mount picks up anything left unfinished and resumes verification, so a
 * user is never left having paid without receiving credits.
 */
export function BuyCreditsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation();
  const invalidateCredits = useInvalidateAiCredits();
  // Any AI feature's balance shows the same shared purchasedBalance - RECEIPT_SCAN
  // is just a convenient one to read here for display purposes.
  const creditsQuery = useAiCredits("RECEIPT_SCAN");

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const [storeProducts, setStoreProducts] = useState<PlayProduct[]>([]);
  const [purchasingId, setPurchasingId] = useState<string | null>(null);
  const packagesRef = useRef<CreditPackage[]>([]);

  const packagesQuery = useQuery({
    queryKey: ["ai-credit-packages"],
    queryFn: ({ signal }) => fetchPackages({ signal }),
  });

  useEffect(() => {
    packagesRef.current = packagesQuery.data ?? [];
  }, [packagesQuery.data]);

  const finishPurchase = useCallback(
    async (purchase: PlayPurchase, matchedPackageId: string | null) => {
      try {
        await verifyPurchase(purchase);
        await RNIap.finishTransaction({ purchase, isConsumable: true });
        invalidateCredits();
        setPurchasingId(null);
        alert("Credits added", "Your AI credits have been topped up.");
        if (matchedPackageId) navigation.goBack();
      } catch (err) {
        // Deliberately do NOT finishTransaction here - if verification failed
        // (e.g. a network blip), leave the purchase unfinished so it's picked
        // up again by getAvailablePurchases next time this screen mounts,
        // instead of silently losing the user's money.
        setPurchasingId(null);
        alert(
          "Couldn't confirm purchase",
          `${getApiErrorMessage(err)} If you were charged, reopen this screen and we'll retry automatically.`,
        );
      }
    },
    [invalidateCredits, navigation],
  );

  useEffect(() => {
    let purchaseUpdateSub: { remove: () => void } | null = null;
    let purchaseErrorSub: { remove: () => void } | null = null;

    (async () => {
      try {
        await RNIap.initConnection();
        if (Platform.OS === "android") {
          await RNIap.flushFailedPurchasesCachedAsPendingAndroid().catch(
            () => {},
          );
        }
        setConnected(true);

        // Resume any purchase that completed but never got verified+finished
        // (e.g. app was killed right after payment).
        const pending = await RNIap.getAvailablePurchases();
        for (const purchase of pending) {
          const matched = packagesRef.current.find(
            (p) => p.googlePlayProductId === purchase.productId,
          );
          finishPurchase(purchase, matched?.id ?? null);
        }
      } catch {
        setConnected(false);
      }
    })();

    purchaseUpdateSub = RNIap.purchaseUpdatedListener((purchase) => {
      const matched = packagesRef.current.find(
        (p) => p.googlePlayProductId === purchase.productId,
      );
      finishPurchase(purchase, matched?.id ?? null);
    });

    purchaseErrorSub = RNIap.purchaseErrorListener((error) => {
      setPurchasingId(null);
      // User-cancelled is not an error worth surfacing.
      if (error.code !== "E_USER_CANCELLED") {
        alert(
          "Purchase failed",
          error.message || "Something went wrong. Please try again.",
        );
      }
    });

    return () => {
      purchaseUpdateSub?.remove();
      purchaseErrorSub?.remove();
      RNIap.endConnection();
    };
  }, [finishPurchase]);

  // Once our backend's package list is in, fetch the matching Play Store
  // product details (localized price, currency) for each one.
  useEffect(() => {
    if (!connected || !packagesQuery.data?.length) return;
    RNIap.getProducts({
      skus: packagesQuery.data.map((p) => p.googlePlayProductId),
    })
      .then(setStoreProducts)
      .catch(() => {});
  }, [connected, packagesQuery.data]);

  useEffect(() => {
    if (!selectedId && packagesQuery.data?.length) {
      const popular = packagesQuery.data.find((p) => p.badge);
      setSelectedId((popular ?? packagesQuery.data[0]).id);
    }
  }, [packagesQuery.data, selectedId]);

  const handleBuy = async () => {
    const pkg = packagesQuery.data?.find((p) => p.id === selectedId);
    if (!pkg) return;
    setPurchasingId(pkg.id);
    try {
      await RNIap.requestPurchase({ skus: [pkg.googlePlayProductId] });
      // Result arrives via purchaseUpdatedListener above, not this promise.
    } catch (err) {
      setPurchasingId(null);
      if (!(err instanceof Error) || !err.message.includes("cancel")) {
        alert(
          "Purchase failed",
          "Something went wrong starting the purchase. Please try again.",
        );
      }
    }
  };

  const localizedPriceFor = (pkg: CreditPackage) =>
    storeProducts.find((sp) => sp.productId === pkg.googlePlayProductId)
      ?.localizedPrice ?? `₹${(pkg.priceInPaise / 100).toFixed(0)}`;

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <View style={styles.header}>
        <Pressable
          onPress={() => navigation.goBack()}
          accessibilityLabel="Back"
          accessibilityRole="button"
          hitSlop={8}
        >
          <ChevronLeft size={26} color={theme.textPrimary} />
        </Pressable>
        <Text style={[styles.headerTitle, { color: theme.textPrimary }]}>
          Buy credits
        </Text>
        <View style={styles.headerSide} />
      </View>

      <View style={styles.intro}>
        <View
          style={[styles.iconWrap, { backgroundColor: theme.primaryContainer }]}
        >
          <Sparkles size={22} color={theme.primary} />
        </View>
        <Text style={[styles.introTitle, { color: theme.textPrimary }]}>
          Power up your AI features
        </Text>
        <Text style={[styles.introSub, { color: theme.textMuted }]}>
          Every AI feature (receipt scanning, voice expense entry, and more to
          come) gets its own free credits each day. Once those run out, they'll
          draw from the same credit pack - buy once, use everywhere.
        </Text>
        {creditsQuery.data ? (
          <Text style={[styles.balance, { color: theme.textSecondary }]}>
            Purchased balance: {creditsQuery.data.purchasedBalance} credit
            {creditsQuery.data.purchasedBalance === 1 ? "" : "s"}
          </Text>
        ) : null}
      </View>

      <View style={styles.list}>
        {packagesQuery.isLoading ? (
          <ActivityIndicator color={theme.primary} />
        ) : (
          packagesQuery.data?.map((pkg) => {
            const selected = pkg.id === selectedId;
            return (
              <Pressable
                key={pkg.id}
                onPress={() => setSelectedId(pkg.id)}
                style={[
                  styles.packageRow,
                  {
                    borderColor: selected ? theme.primary : theme.border,
                    backgroundColor: selected
                      ? theme.primaryContainer
                      : theme.surface,
                  },
                ]}
              >
                <View style={styles.packageInfo}>
                  <Text
                    style={[
                      styles.packageCredits,
                      { color: theme.textPrimary },
                    ]}
                  >
                    {pkg.credits} credits
                  </Text>
                  {pkg.badge ? (
                    <Text
                      style={[styles.packageBadge, { color: theme.primary }]}
                    >
                      {pkg.badge}
                    </Text>
                  ) : null}
                </View>
                <View style={styles.packageRight}>
                  <Text
                    style={[styles.packagePrice, { color: theme.textPrimary }]}
                  >
                    {localizedPriceFor(pkg)}
                  </Text>
                  {selected ? <Check size={18} color={theme.primary} /> : null}
                </View>
              </Pressable>
            );
          })
        )}
      </View>

      <View style={styles.footer}>
        <Button
          title={purchasingId ? "Processing…" : "Buy selected pack"}
          disabled={!selectedId || !connected || purchasingId !== null}
          onPress={handleBuy}
        />
        <View style={styles.secureRow}>
          <ShieldCheck size={13} color={theme.textMuted} />
          <Text style={[styles.secureText, { color: theme.textMuted }]}>
            Payments are handled securely by Google Play
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerSide: { flex: 1, flexDirection: "row", alignItems: "center" },
  headerTitle: {
    flex: 1,
    textAlign: "center",
    fontSize: 16,
    fontWeight: "700",
  },
  intro: { alignItems: "center", paddingHorizontal: 28, paddingTop: 8 },
  iconWrap: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 12,
  },
  introTitle: { fontSize: 18, fontWeight: "800", marginBottom: 6 },
  introSub: { fontSize: 13, textAlign: "center", lineHeight: 19 },
  balance: { fontSize: 12, fontWeight: "600", marginTop: 14 },
  list: { paddingHorizontal: 20, paddingTop: 24, gap: 10 },
  packageRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  packageInfo: { gap: 2 },
  packageCredits: { fontSize: 15, fontWeight: "700" },
  packageBadge: { fontSize: 11, fontWeight: "700" },
  packageRight: { flexDirection: "row", alignItems: "center", gap: 8 },
  packagePrice: { fontSize: 15, fontWeight: "800" },
  footer: { paddingHorizontal: 20, paddingTop: 20, paddingBottom: 12, gap: 10 },
  secureRow: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 6,
  },
  secureText: { fontSize: 11 },
});
