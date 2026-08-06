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
