import React from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Pressable,
} from "react-native";
import { useForm, Controller } from "react-hook-form";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation } from "@tanstack/react-query";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage, getApiErrorCode } from "@/lib/apiClient";
import { Logo } from "@/components/Logo";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { ThemeToggle } from "@/components/ThemeToggle";
import { AuthStackParamList } from "@/navigation/types";
import { GoogleSignInButton } from "@/components/GoogleSignInButton";
import { storage, StorageKeys } from "@/lib/storage";

type FormValues = { email: string; password: string };
type Nav = NativeStackNavigationProp<AuthStackParamList, "Login">;

export function LoginScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const login = useAuthStore((s) => s.login);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ defaultValues: { email: "", password: "" } });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => login(values),

    onError: (error, variables) => {
      const code = getApiErrorCode(error);

      if (code === "EMAIL_NOT_VERIFIED") {
        storage.set(StorageKeys.PENDING_EMAIL, variables.email);
        navigation.replace("VerifyEmail", {
          email: variables.email,
        });
        return;
      }

      if (code === "VERIFICATION_EXPIRED") {
        storage.delete(StorageKeys.PENDING_EMAIL);
        navigation.replace("Signup");
        return;
      }
    },
  });

  const onSubmit = (values: FormValues) => mutation.mutate(values);

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={styles.flex}
      >
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.topRow}>
            <View />
            <ThemeToggle />
          </View>

          <View style={styles.header}>
            <Logo size={88} />
            <Text style={[styles.title, { color: theme.textPrimary }]}>
              Welcome back
            </Text>
            <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
              Log in to keep splitting expenses with Splenza
            </Text>
          </View>

          <Controller
            control={control}
            name="email"
            rules={{
              required: "Email is required",
              pattern: {
                value: /^\S+@\S+\.\S+$/,
                message: "Enter a valid email",
              },
            }}
            render={({ field: { onChange, value } }) => (
              <TextField
                label="Email"
                value={value}
                onChangeText={onChange}
                autoCapitalize="none"
                keyboardType="email-address"
                placeholder="you@example.com"
                error={errors.email?.message}
              />
            )}
          />

          <Controller
            control={control}
            name="password"
            rules={{ required: "Password is required" }}
            render={({ field: { onChange, value } }) => (
              <TextField
                label="Password"
                value={value}
                onChangeText={onChange}
                secureTextEntry
                placeholder="••••••••"
                error={errors.password?.message}
              />
            )}
          />

          {mutation.isError ? (
            <Text style={[styles.formError, { color: theme.danger }]}>
              {getApiErrorMessage(mutation.error)}
            </Text>
          ) : null}

          <Pressable
            onPress={() => navigation.navigate("ForgotPassword")}
            style={{
              alignSelf: "flex-end",
              marginTop: -6,
              marginBottom: 18,
            }}
          >
            <Text
              style={{
                color: theme.primary,
                fontWeight: "600",
              }}
            >
              Forgot Password?
            </Text>
          </Pressable>

          <Button
            title="Log In"
            onPress={handleSubmit(onSubmit)}
            loading={mutation.isPending}
          />

          <View style={styles.dividerRow}>
            <View
              style={[styles.dividerLine, { backgroundColor: theme.border }]}
            />
            <Text style={{ color: theme.textMuted, fontSize: 12 }}>OR</Text>
            <View
              style={[styles.dividerLine, { backgroundColor: theme.border }]}
            />
          </View>

          <GoogleSignInButton onError={(msg) => console.warn(msg)} />

          <Pressable
            onPress={() => navigation.navigate("Signup")}
            style={styles.footerLink}
          >
            <Text style={{ color: theme.textSecondary }}>
              Don't have an account?{" "}
              <Text style={{ color: theme.primary, fontWeight: "700" }}>
                Sign up
              </Text>
            </Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { flexGrow: 1, padding: 24, justifyContent: "center" },
  topRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    position: "absolute",
    top: 8,
    left: 24,
    right: 24,
  },
  header: { alignItems: "center", marginBottom: 32, gap: 8 },
  title: { fontSize: 26, fontWeight: "800", marginTop: 12 },
  subtitle: { fontSize: 14, textAlign: "center" },
  formError: { textAlign: "center", marginBottom: 12, fontSize: 13 },
  footerLink: { marginTop: 20, alignItems: "center" },
  dividerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginVertical: 16,
  },
  dividerLine: { flex: 1, height: 1 },
});
