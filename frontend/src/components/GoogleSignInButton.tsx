import React from "react";
import { Pressable, Text, StyleSheet, ActivityIndicator } from "react-native";

import {
  GoogleSignin,
  isSuccessResponse,
  statusCodes,
} from "@react-native-google-signin/google-signin";

import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";

interface GoogleSignInButtonProps {
  onError?: (message: string) => void;
}

export function GoogleSignInButton({ onError }: GoogleSignInButtonProps) {
  const { theme } = useAppTheme();

  const loginWithGoogleIdToken = useAuthStore((s) => s.loginWithGoogleIdToken);

  const [loading, setLoading] = React.useState(false);

  const handleGoogleLogin = async () => {
    try {
      setLoading(true);

      await GoogleSignin.hasPlayServices();

      const response = await GoogleSignin.signIn();

      if (!isSuccessResponse(response)) {
        return;
      }

      const idToken = response.data.idToken;

      if (!idToken) {
        throw new Error("No ID Token received.");
      }

      await loginWithGoogleIdToken(idToken);
    } catch (err: any) {
      console.log("GOOGLE ERROR:", JSON.stringify(err, null, 2));
      console.log("GOOGLE ERROR RAW:", err);

      if (err.code === statusCodes.SIGN_IN_CANCELLED) {
        return;
      }

      if (err.code === statusCodes.IN_PROGRESS) {
        return;
      }

      if (err.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        onError?.("Google Play Services unavailable.");
        return;
      }

      onError?.(err.message ?? "Google Sign-In failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Pressable
      disabled={loading}
      onPress={handleGoogleLogin}
      style={[
        styles.button,
        {
          backgroundColor: theme.surface,
          borderColor: theme.border,
        },
      ]}
    >
      {loading ? (
        <ActivityIndicator color={theme.textPrimary} />
      ) : (
        <Text
          style={[
            styles.text,
            {
              color: theme.textPrimary,
            },
          ]}
        >
          Continue with Google
        </Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    borderWidth: 1,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: "center",
    justifyContent: "center",
  },

  text: {
    fontWeight: "700",
    fontSize: 15,
  },
});
