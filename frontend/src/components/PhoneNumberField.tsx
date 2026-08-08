import React from "react";
import { View, Text, TextInput, StyleSheet } from "react-native";
import { useAppTheme } from "@/theme/ThemeContext";

interface PhoneNumberFieldProps {
  label?: string;
  /** Full E.164 value, e.g. "+919876543210". Empty string when untouched. */
  value: string;
  /** Called with the full E.164 value (country code + digits) on every change. */
  onChangeText: (fullNumber: string) => void;
  error?: string;
  /** Defaults to "+91" - change if the app ever needs to support other countries. */
  countryCode?: string;
}

/**
 * Phone input with a fixed, non-editable country-code prefix so people don't
 * have to type "+91" themselves every time. Only the local digits are
 * editable; the field reports the full E.164 number to the caller.
 */
export function PhoneNumberField({
  label = "Phone number",
  value,
  onChangeText,
  error,
  countryCode = "+91",
}: PhoneNumberFieldProps) {
  const { theme } = useAppTheme();

  const localDigits = value.startsWith(countryCode)
    ? value.slice(countryCode.length)
    : value.replace(/[^0-9]/g, "");

  const handleChange = (text: string) => {
    const digits = text.replace(/[^0-9]/g, "").slice(0, 10);
    onChangeText(digits ? `${countryCode}${digits}` : "");
  };

  return (
    <View style={styles.wrapper}>
      <Text style={[styles.label, { color: theme.textSecondary }]}>
        {label}
      </Text>
      <View
        style={[
          styles.row,
          {
            backgroundColor: theme.surface,
            borderColor: error ? theme.danger : theme.border,
          },
        ]}
      >
        <View style={[styles.prefix, { borderRightColor: theme.border }]}>
          <Text style={[styles.prefixText, { color: theme.textPrimary }]}>
            {countryCode}
          </Text>
        </View>
        <TextInput
          value={localDigits}
          onChangeText={handleChange}
          keyboardType="number-pad"
          maxLength={10}
          placeholder="98765 43210"
          placeholderTextColor={theme.textMuted}
          style={[styles.input, { color: theme.textPrimary }]}
        />
      </View>
      {error ? (
        <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { marginBottom: 16 },
  label: { fontSize: 13, fontWeight: "600", marginBottom: 6 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderRadius: 12,
  },
  prefix: {
    paddingHorizontal: 14,
    paddingVertical: 14,
    borderRightWidth: 1,
  },
  prefixText: { fontSize: 15, fontWeight: "700" },
  input: {
    flex: 1,
    paddingHorizontal: 14,
    paddingVertical: 14,
    fontSize: 15,
  },
  error: { fontSize: 12, marginTop: 4 },
});
