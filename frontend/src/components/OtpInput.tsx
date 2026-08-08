import React, { useRef } from "react";
import { TextInput, View, StyleSheet } from "react-native";
import { useAppTheme } from "@/theme/ThemeContext";

interface OtpInputProps {
  value: string[];
  onChange: (value: string[]) => void;
  /** Called once all 6 digits are filled in. */
  onComplete?: () => void;
}

/**
 * Six-box OTP input. Shared by every OTP flow (email verify, phone
 * signup/login, adding & verifying a second identifier) so the behavior -
 * auto-advance, paste-to-fill, backspace-to-previous, auto-submit on
 * completion - stays identical everywhere.
 */
export function OtpInput({ value, onChange, onComplete }: OtpInputProps) {
  const { theme } = useAppTheme();
  const inputs = useRef<TextInput[]>([]);

  const handleChange = (text: string, index: number) => {
    if (text.length > 1) {
      const pasted = text.slice(0, 6).split("");
      const copy = [...value];
      pasted.forEach((v, i) => {
        if (index + i < 6) copy[index + i] = v;
      });
      onChange(copy);
      const completed = copy.every((d) => d.length === 1);
      if (completed) setTimeout(() => onComplete?.(), 150);
      return;
    }

    const copy = [...value];
    copy[index] = text;
    onChange(copy);

    if (text && index < 5) {
      inputs.current[index + 1]?.focus();
    }

    const completed = copy.every((d) => d.length === 1);
    if (completed) {
      setTimeout(() => onComplete?.(), 150);
    }
  };

  const handleBackspace = (text: string, index: number) => {
    if (text === "" && index > 0) {
      inputs.current[index - 1]?.focus();
    }
  };

  return (
    <View style={styles.row}>
      {value.map((digit, index) => (
        <TextInput
          key={index}
          ref={(ref) => {
            if (ref) inputs.current[index] = ref;
          }}
          value={digit}
          onChangeText={(t) => handleChange(t, index)}
          onKeyPress={({ nativeEvent }) => {
            if (nativeEvent.key === "Backspace") {
              handleBackspace(digit, index);
            }
          }}
          keyboardType="number-pad"
          maxLength={6}
          autoComplete="sms-otp"
          textContentType="oneTimeCode"
          style={[
            styles.box,
            { borderColor: theme.border, color: theme.textPrimary },
          ]}
        />
      ))}
    </View>
  );
}

export const EMPTY_OTP = ["", "", "", "", "", ""];

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  box: {
    width: 52,
    height: 58,
    borderWidth: 1,
    borderRadius: 14,
    fontSize: 22,
    fontWeight: "700",
    textAlign: "center",
  },
});
