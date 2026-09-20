import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { useQuery } from "@tanstack/react-query";
import { Sparkles, Check, ShieldCheck } from "lucide-react-native";
import {
  useIAP,
  ErrorCode,
  type Purchase,
  type Product,
} from "react-native-iap";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { alert } from "@/components/AppAlert";
import { Button } from "@/components/Button";
import { ScreenHeader } from "@/components/ScreenHeader";
import { useAiCredits, useInvalidateAiCredits } from "@/hooks/useAiCredits";
import { CreditPackage } from "@/types/api";

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
async function verifyPurchase(purchase: Purchase) {
  await apiClient.post("/api/v1/ai-credits/purchases/verify", {
    productId: purchase.productId,
    purchaseToken: purchase.purchaseToken,
  });
}

/**
 * Lets the user top up their shared AI-credit wallet (spendable on receipt
 * scanning, voice expense entry, and any future AI feature) via Google Play
 * Billing. Packages are managed (consumable) in-app products in Play
 * Console; `CreditPackage.googlePlayProductId` maps each one to its SKU.
 *
 * Flow: fetch products from Play -> user picks one -> requestPurchase ->
 * onPurchaseSuccess fires -> send the purchase to our backend to verify
 * server-side against the Play Developer API and grant credits -> only then
 * finishTransaction (consumable) so the item becomes re-purchasable.
 *
 * Note: unlike the old react-native-iap API, this version's restore/resume
 * API (getAvailablePurchases) explicitly excludes consumables (per the
 * library's own docs), so we can't use it to pick back up an
 * interrupted-but-unfinished credit purchase after an app kill. In practice
 * Google Play Billing itself re-delivers any not-yet-acknowledged purchase
 * through the same purchase-update event the moment the connection
 * (re)initializes, which is exactly what `onPurchaseSuccess` below is wired
 * to - so an interrupted purchase still gets picked up as soon as this
 * screen is reopened and the connection comes back up, without needing a
 * separate manual restore step.
 */
export function BuyCreditsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation();
  const invalidateCredits = useInvalidateAiCredits();
  // Any AI feature's balance shows the same shared purchasedBalance - RECEIPT_SCAN
  // is just a convenient one to read here for display purposes.
  const creditsQuery = useAiCredits("RECEIPT_SCAN");

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [purchasingId, setPurchasingId] = useState<string | null>(null);
  const [productsFetched, setProductsFetched] = useState(false);
  const packagesRef = useRef<CreditPackage[]>([]);

  const packagesQuery = useQuery({
    queryKey: ["ai-credit-packages"],
    queryFn: ({ signal }) => fetchPackages({ signal }),
  });

  useEffect(() => {
    packagesRef.current = packagesQuery.data ?? [];
  }, [packagesQuery.data]);

  const {
    connected,
    products,
    fetchProducts,
    requestPurchase,
    finishTransaction,
  } = useIAP({
    onPurchaseSuccess: async (purchase) => {
      const matched = packagesRef.current.find(
        (p) => p.googlePlayProductId === purchase.productId,
      );
      try {
        await verifyPurchase(purchase);
        await finishTransaction({ purchase, isConsumable: true });
        invalidateCredits();
        setPurchasingId(null);
        alert("Credits added", "Your AI credits have been topped up.");
        if (matched) navigation.goBack();
      } catch (err) {
        // Deliberately do NOT finishTransaction here - if verification
        // failed (e.g. a network blip), leave the purchase unfinished so
        // Google Play redelivers it the next time the connection
        // initializes, instead of silently losing the user's money.
        setPurchasingId(null);
        alert(
          "Couldn't confirm purchase",
          `${getApiErrorMessage(err)} If you were charged, reopen this screen and we'll retry automatically.`,
        );
      }
    },
    onPurchaseError: (error) => {
      setPurchasingId(null);
      if (error.code !== ErrorCode.UserCancelled) {
        alert(
          "Purchase failed",
          error.message || "Something went wrong. Please try again.",
        );
      }
    },
  });

  // Once our backend's package list is in and the store connection is up,
  // fetch the matching Play Store product details (localized price, etc.)
  // for each one.
  useEffect(() => {
    if (!connected || !packagesQuery.data?.length) return;
    fetchProducts({
      skus: packagesQuery.data.map((p) => p.googlePlayProductId),
      type: "in-app",
    })
      .catch(() => {})
      .finally(() => setProductsFetched(true));
  }, [connected, packagesQuery.data, fetchProducts]);

  // A package only shows up here if Play actually returned a matching
  // product for it - if a product was deactivated/removed in Play Console
  // (or never went live), Play simply won't include it in `products`, and
  // we hide it rather than let someone try to buy something Play will
  // reject. Until the fetch has settled we show everything the backend
  // returned so the list isn't empty while still loading.
  const availablePackages = !productsFetched
    ? packagesQuery.data
    : packagesQuery.data?.filter((pkg) =>
        products.some((p) => p.id === pkg.googlePlayProductId),
      );
  const noActiveProducts =
    connected &&
    productsFetched &&
    (packagesQuery.data?.length ?? 0) > 0 &&
    (availablePackages?.length ?? 0) === 0;

  useEffect(() => {
    if (!selectedId && availablePackages?.length) {
      const popular = availablePackages.find((p) => p.badge);
      setSelectedId((popular ?? availablePackages[0]).id);
    }
  }, [availablePackages, selectedId]);

  const handleBuy = async () => {
    const pkg = availablePackages?.find((p) => p.id === selectedId);
    if (!pkg) return;
    setPurchasingId(pkg.id);
    try {
      await requestPurchase({
        request: { google: { skus: [pkg.googlePlayProductId] } },
        type: "in-app",
      });
      // Result arrives via onPurchaseSuccess/onPurchaseError above, not this promise.
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

  const storeProductFor = (pkg: CreditPackage): Product | undefined =>
    products.find((sp) => sp.id === pkg.googlePlayProductId);

  const localizedPriceFor = (pkg: CreditPackage) =>
    storeProductFor(pkg)?.displayPrice ??
    `₹${(pkg.priceInPaise / 100).toFixed(0)}`;

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title="Buy credits" />

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
        ) : noActiveProducts ? (
          <View
            style={[
              styles.pausedBox,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Text style={[styles.pausedText, { color: theme.textSecondary }]}>
              Buying credits is paused for now. Please check back later.
            </Text>
          </View>
        ) : (
          availablePackages?.map((pkg) => {
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
        {!connected ? (
          <Text style={[styles.connectionError, { color: theme.danger }]}>
            Couldn't connect to Google Play. Make sure you're signed into a
            Google account and the Play Store app is set up on this device, then
            reopen this screen.
          </Text>
        ) : null}
        <Button
          title={purchasingId ? "Processing…" : "Buy selected pack"}
          disabled={
            !selectedId ||
            !connected ||
            purchasingId !== null ||
            noActiveProducts
          }
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
  pausedBox: {
    borderWidth: 1,
    borderRadius: 14,
    paddingVertical: 20,
    paddingHorizontal: 16,
    alignItems: "center",
  },
  pausedText: { fontSize: 13, textAlign: "center", lineHeight: 19 },
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
  connectionError: { fontSize: 12, textAlign: "center", lineHeight: 17 },
  secureRow: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 6,
  },
  secureText: { fontSize: 11 },
});
