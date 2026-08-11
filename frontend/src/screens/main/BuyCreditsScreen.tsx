import React from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, Sparkles, Check } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { alert } from "@/components/AppAlert";
import { Button } from "@/components/Button";
import { useReceiptCredits } from "@/hooks/useReceiptCredits";
import { CreditPackage } from "@/types/api";

async function fetchPackages({
  signal,
}: {
  signal: AbortSignal;
}): Promise<CreditPackage[]> {
  const { data } = await apiClient.get<CreditPackage[]>(
    "/api/v1/receipt-scans/packages",
    { signal },
  );
  return data;
}

function formatPrice(paise: number, currency: string) {
  const amount = (paise / 100).toFixed(0);
  return currency === "INR" ? `₹${amount}` : `${currency} ${amount}`;
}

/**
 * Lets the user top up receipt-scan credits. The purchase itself goes
 * through `/api/v1/receipt-scans/purchase`, which today just returns a
 * pending-payment order - actually collecting payment needs a gateway SDK
 * (Razorpay/Stripe) wired in here before this can charge real money. See
 * the TODO on `purchaseMutation` below.
 */
export function BuyCreditsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation();
  const queryClient = useQueryClient();
  const creditsQuery = useReceiptCredits();
  const [selectedId, setSelectedId] = React.useState<string | null>(null);

  const packagesQuery = useQuery({
    queryKey: ["receipt-scan-packages"],
    queryFn: ({ signal }) => fetchPackages({ signal }),
  });

  React.useEffect(() => {
    if (!selectedId && packagesQuery.data?.length) {
      const popular = packagesQuery.data.find((p) => p.badge);
      setSelectedId((popular ?? packagesQuery.data[0]).id);
    }
  }, [packagesQuery.data, selectedId]);

  const purchaseMutation = useMutation({
    mutationFn: (packageId: string) =>
      apiClient.post("/api/v1/receipt-scans/purchase", { packageId }),
    onSuccess: () => {
      // TODO: this currently just confirms the order server-side. Once a
      // payment gateway (e.g. Razorpay Checkout) is wired in, this should
      // instead open the gateway's checkout with the returned order id,
      // and only invalidate/navigate back after the payment callback
      // confirms success.
      queryClient.invalidateQueries({ queryKey: ["receipt-scan-credits"] });
      alert("Credits added", "Your receipt-scan credits have been topped up.");
      navigation.goBack();
    },
    onError: (err) => alert("Purchase failed", getApiErrorMessage(err)),
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <View style={styles.header}>
        <View style={styles.headerSide}>
          <Pressable
            onPress={() => navigation.goBack()}
            accessibilityLabel="Back"
            accessibilityRole="button"
            hitSlop={8}
          >
            <ChevronLeft size={26} color={theme.textPrimary} />
          </Pressable>
        </View>
        <Text
          style={[styles.headerTitle, { color: theme.textPrimary }]}
          numberOfLines={1}
        >
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
          Scan receipts with AI
        </Text>
        <Text style={[styles.introSub, { color: theme.textMuted }]}>
          You get 2 free scans every day. Buy credits to keep scanning once
          you've used them up - they never expire.
        </Text>
        {creditsQuery.data ? (
          <Text style={[styles.balance, { color: theme.textSecondary }]}>
            Current balance: {creditsQuery.data.totalAvailable} credit
            {creditsQuery.data.totalAvailable === 1 ? "" : "s"} (
            {creditsQuery.data.freeRemaining} free today +{" "}
            {creditsQuery.data.purchasedBalance} purchased)
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
                    {formatPrice(pkg.priceInPaise, pkg.currency)}
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
          title={
            purchaseMutation.isPending ? "Processing…" : "Buy selected pack"
          }
          disabled={!selectedId || purchaseMutation.isPending}
          onPress={() => selectedId && purchaseMutation.mutate(selectedId)}
        />
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
  footer: { paddingHorizontal: 20, paddingTop: 20, paddingBottom: 12 },
});
