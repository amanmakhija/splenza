import * as ImagePicker from "expo-image-picker";

/**
 * Requests photo-library permission (if needed) and opens the picker,
 * cropped to a square. Returns the picked image's local URI, or null if the
 * person cancelled or denied permission.
 */
export async function pickSquareImage(): Promise<string | null> {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) return null;

  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.Images,
    allowsEditing: true,
    aspect: [1, 1],
    quality: 0.8,
  });

  if (result.canceled) return null;
  return result.assets[0].uri;
}

/**
 * Opens the camera or photo library (uncropped, full-frame) to capture a
 * receipt photo for scanning. Unlike `pickSquareImage`, this deliberately
 * does not force a square crop - receipts are tall/rectangular and cropping
 * to square would cut off line items.
 */
export async function pickReceiptImage(
  source: "camera" | "library",
): Promise<string | null> {
  if (source === "camera") {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) return null;
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.85,
    });
    if (result.canceled) return null;
    return result.assets[0].uri;
  }

  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) return null;
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.Images,
    quality: 0.85,
  });
  if (result.canceled) return null;
  return result.assets[0].uri;
}
