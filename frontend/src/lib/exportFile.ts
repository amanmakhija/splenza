import * as FileSystem from "expo-file-system";
import * as Sharing from "expo-sharing";
import { Alert } from "react-native";
import { apiClient } from "./apiClient";
import { storage, StorageKeys } from "./storage";

/**
 * Downloads a file from an authenticated backend endpoint and opens the OS share sheet
 * (which on both iOS and Android also offers "Save to Files"/"Save to device").
 */
export async function downloadAndShare(
  path: string,
  filename: string,
  mimeType: string,
): Promise<void> {
  const baseUrl = apiClient.defaults.baseURL;
  const token = storage.getString(StorageKeys.ACCESS_TOKEN);
  const fileUri = `${FileSystem.cacheDirectory}${filename}`;

  try {
    const result = await FileSystem.downloadAsync(
      `${baseUrl}${path}`,
      fileUri,
      {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      },
    );

    if (result.status !== 200) {
      throw new Error(`Download failed with status ${result.status}`);
    }

    if (await Sharing.isAvailableAsync()) {
      await Sharing.shareAsync(result.uri, { mimeType, dialogTitle: filename });
    } else {
      Alert.alert("Saved", `File saved to ${result.uri}`);
    }
  } catch (err) {
    Alert.alert(
      "Export failed",
      "Could not export this file. Please try again.",
    );
  }
}
