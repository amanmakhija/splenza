import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import Constants from "expo-constants";
import { RefreshCw } from "lucide-react-native";

import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { openAppStoreListing } from "@/lib/appStore";
import { isVersionNewer } from "@/lib/compareVersions";
import { storage, StorageKeys } from "@/lib/storage";
import { AppModal } from "@/components/AppModal";
import { Button } from "@/components/Button";

const CURRENT_VERSION = Constants.expoConfig?.version ?? "1.0.0";

interface AppConfigResponse {
  latestVersion: string;
  /** Optional short note about what's new - shown under the title if present. */
  releaseNotes?: string | null;
}

/**
 * Checks once at startup whether a newer version is available and, if so,
 * shows a dismissible "Update available" prompt with an "Update Now" button
 * that opens the store listing, and a "Later" button that just closes it.
 * Updating is always optional - this never blocks app usage.
 *
 * Mount once near the root (e.g. in RootNavigator), same as AlertHost.
 */
export function UpdatePrompt() {
  const { theme } = useAppTheme();
  const [latestVersion, setLatestVersion] = useState<string | null>(null);
  const [releaseNotes, setReleaseNotes] = useState<string | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const { data } =
          await apiClient.get<AppConfigResponse>("/api/v1/app-config");

        if (cancelled) return;
        if (!isVersionNewer(data.latestVersion, CURRENT_VERSION)) return;

        // Don't nag again about a version the person already dismissed -
        // they'll still be prompted the next time a *newer* version ships.
        const skipped = storage.getString(StorageKeys.SKIPPED_UPDATE_VERSION);
        if (skipped === data.latestVersion) return;

        setLatestVersion(data.latestVersion);
        setReleaseNotes(data.releaseNotes ?? null);
        setVisible(true);
      } catch {
        // No network, endpoint not deployed yet, etc. - fail silently,
        // an update prompt is never worth interrupting someone over.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  const handleLater = () => {
    if (latestVersion) {
      storage.set(StorageKeys.SKIPPED_UPDATE_VERSION, latestVersion);
    }
    setVisible(false);
  };

  const handleUpdate = () => {
    openAppStoreListing();
    setVisible(false);
  };

  return (
    <AppModal visible={visible} onClose={handleLater} scrollable={false}>
      <View
        style={[styles.iconWrap, { backgroundColor: `${theme.primary}1A` }]}
      >
        <RefreshCw size={22} color={theme.primary} />
      </View>

      <Text style={[styles.title, { color: theme.textPrimary }]}>
        Update available
      </Text>

      <Text style={[styles.body, { color: theme.textSecondary }]}>
        {releaseNotes ??
          `A new version of Splenza (${latestVersion ?? ""}) is ready. Update whenever it suits you.`}
      </Text>

      <Button title="Update Now" onPress={handleUpdate} />

      <Button
        title="Later"
        variant="outline"
        onPress={handleLater}
        style={{ marginTop: 8 }}
      />
    </AppModal>
  );
}

const styles = StyleSheet.create({
  iconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 14,
  },
  title: { fontSize: 17, fontWeight: "700", marginBottom: 8 },
  body: { fontSize: 14, lineHeight: 20, marginBottom: 20 },
});
