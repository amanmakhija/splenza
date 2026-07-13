import React, { useEffect } from "react";
import { Pressable, Text, StyleSheet, ActivityIndicator } from "react-native";
import * as WebBrowser from "expo-web-browser";
import * as Google from "expo-auth-session/providers/google";
import Constants from "expo-constants";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";

WebBrowser.maybeCompleteAuthSession();

interface GoogleSignInButtonProps {
  onError?: (message: string) => void;
}

const webClientId = Constants.expoConfig?.extra?.googleWebClientId as
  | string
  | undefined;

export function GoogleSignInButton({ onError }: GoogleSignInButtonProps) {
  const { theme } = useAppTheme();
  const loginWithGoogleIdToken = useAuthStore((s) => s.loginWithGoogleIdToken);
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  const [request, response, promptAsync] = Google.useAuthRequest({
    // Same web client ID as the backend's GOOGLE_OAUTH_CLIENT_ID (must match for the ID token
    // audience check on the server to pass). See backend README for how to create this.
    clientId: webClientId,
    scopes: ["openid", "profile", "email"],
  });

  useEffect(() => {
    const handleResponse = async () => {
      if (response?.type === "success" && response.params.id_token) {
        setIsSubmitting(true);
        try {
          await loginWithGoogleIdToken(response.params.id_token);
        } catch (err) {
          onError?.(getApiErrorMessage(err, "Google sign-in failed"));
        } finally {
          setIsSubmitting(false);
        }
      } else if (response?.type === "error") {
        onError?.("Google sign-in was cancelled or failed");
      }
    };
    handleResponse();
  }, [response, loginWithGoogleIdToken, onError]);

  const isConfigured =
    Boolean(webClientId) && !webClientId?.startsWith("REPLACE_WITH");

  if (!isConfigured) {
    // Silently hide the button rather than show a broken flow - see README for setup steps.
    return null;
  }

  return (
    <Pressable
      disabled={!request || isSubmitting}
      onPress={() => promptAsync()}
      style={[
        styles.button,
        { backgroundColor: theme.surface, borderColor: theme.border },
      ]}
    >
      {isSubmitting ? (
        <ActivityIndicator color={theme.textPrimary} />
      ) : (
        <Text style={[styles.text, { color: theme.textPrimary }]}>
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
  text: { fontWeight: "700", fontSize: 15 },
});
