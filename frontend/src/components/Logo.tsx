import React from "react";
import Svg, { Path, Circle } from "react-native-svg";
import { brand } from "@/theme/colors";

interface LogoProps {
  size?: number;
  /** Defaults to the brand primary. Pass "#FFFFFF" when placing on a colored/dark surface. */
  color?: string;
}

/** The Splenza mark - two arcing arrows forming a loop, rendered from the real vector source
 *  (assets/logo/splenza-mark.svg) so it's crisp at any size and recolorable. */
export function Logo({ size = 64, color = brand.primary }: LogoProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100" fill="none">
      <Path
        d="M16.67,50 C16.67,31.67 31.67,16.67 50,16.67 C62.5,16.67 73.33,23.75 79.17,34.17"
        stroke={color}
        strokeWidth={10.5}
        strokeLinecap="round"
        fill="none"
      />
      <Path
        d="M83.33,50 C83.33,68.33 68.33,83.33 50,83.33 C37.5,83.33 26.67,76.25 20.83,65.83"
        stroke={color}
        strokeWidth={10.5}
        strokeLinecap="round"
        fill="none"
      />
      <Circle cx={33.33} cy={33.33} r={6.5} fill={color} />
      <Circle cx={66.67} cy={66.67} r={6.5} fill={color} />
    </Svg>
  );
}
