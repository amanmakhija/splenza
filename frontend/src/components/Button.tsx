import React from "react";
import { Pressable, Text, ActivityIndicator, StyleSheet, ViewStyle, StyleProp } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { brand } from "@/theme/colors";

interface ButtonProps {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: "primary" | "outline";
  style?: StyleProp<ViewStyle>;
}

export function Button({ title, onPress, loading, disabled, variant = "primary", style }: ButtonProps) {
  const isDisabled = disabled || loading;

  if (variant === "outline") {
    return (
      <Pressable
        onPress={onPress}
        disabled={isDisabled}
        style={[styles.outline, isDisabled && styles.disabled, style]}
      >
        <Text style={styles.outlineText}>{title}</Text>
      </Pressable>
    );
  }

  return (
    <Pressable onPress={onPress} disabled={isDisabled} style={[styles.shadowWrap, style]}>
      <LinearGradient
        colors={[brand.primaryPurple, brand.secondaryBlue]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[styles.gradient, isDisabled && styles.disabled]}
      >
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.text}>{title}</Text>}
      </LinearGradient>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  shadowWrap: {
    borderRadius: 14,
    shadowColor: brand.primaryPurple,
    shadowOpacity: 0.3,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4
  },
  gradient: {
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: "center",
    justifyContent: "center"
  },
  outline: {
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderColor: brand.primaryPurple
  },
  outlineText: { color: brand.primaryPurple, fontWeight: "700", fontSize: 15 },
  text: { color: "#fff", fontWeight: "700", fontSize: 15 },
  disabled: { opacity: 0.5 }
});
