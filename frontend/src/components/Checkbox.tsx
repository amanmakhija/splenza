import React from "react";
import { Pressable, View, Text, StyleSheet } from "react-native";
import { Check } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";

interface CheckboxProps {
  label: string;
  subLabel?: string;
  checked: boolean;
  onToggle: () => void;
}

export function Checkbox({
  label,
  subLabel,
  checked,
  onToggle,
}: CheckboxProps) {
  const { theme } = useAppTheme();

  return (
    <Pressable onPress={onToggle} style={styles.row}>
      <View
        style={[
          styles.box,
          {
            backgroundColor: checked ? theme.primary : "transparent",
            borderColor: checked ? theme.primary : theme.border,
          },
        ]}
      >
        {checked ? <Check size={14} color="#fff" /> : null}
      </View>
      <View style={styles.textWrap}>
        <Text style={[styles.label, { color: theme.textPrimary }]}>
          {label}
        </Text>
        {subLabel ? (
          <Text style={[styles.subLabel, { color: theme.textMuted }]}>
            {subLabel}
          </Text>
        ) : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 10,
    gap: 12,
  },
  box: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
  },
  textWrap: { flex: 1 },
  label: { fontSize: 15, fontWeight: "600" },
  subLabel: { fontSize: 12, marginTop: 1 },
});
