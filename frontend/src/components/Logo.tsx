import React from "react";
import { Image } from "expo-image";
import { StyleProp, ViewStyle } from "react-native";

interface LogoProps {
  size?: number;
  /** "mark" = transparent swirl only, use on colored/dark backgrounds you control.
   *  "tile" = the rounded dark-square app-icon version, use when you want a self-contained badge. */
  variant?: "mark" | "tile";
  style?: StyleProp<ViewStyle>;
}

const sources = {
  mark: require("../../assets/logo/splentra-logo.svg"),
  tile: require("../../assets/logo/splentra-logo.svg"),
};

export function Logo({ size = 64, variant = "mark", style }: LogoProps) {
  return (
    <Image
      source={sources[variant]}
      style={[{ width: size, height: size }, style]}
      contentFit="contain"
      transition={150}
    />
  );
}
