import {
  Tag,
  UtensilsCrossed,
  Car,
  ShoppingBag,
  Home,
  Film,
  Plane,
  HeartPulse,
  Zap,
  type LucideIcon,
} from "lucide-react-native";

interface CategoryStyle {
  icon: LucideIcon;
  /** Icon-wrap background. Fixed per category (not theme-dependent) so each
   * category stays visually distinct and recognizable at a glance across
   * light/dark mode, the way a color-coded category system should. */
  color: string;
}

// One fixed accent per category group. Chosen to be distinguishable from
// each other and from the semantic owed/owe/reminder colors used elsewhere
// in the app (see theme/colors.ts), so a category chip is never mistaken
// for a balance indicator.
const CATEGORY_STYLES: Record<string, CategoryStyle> = {
  food: { icon: UtensilsCrossed, color: "#E0793C" }, // warm orange
  transport: { icon: Car, color: "#3B82C4" }, // steel blue
  shopping: { icon: ShoppingBag, color: "#8B5FBF" }, // purple
  home: { icon: Home, color: "#2E9E8F" }, // teal
  entertainment: { icon: Film, color: "#6B6E76" }, // slate gray
  travel: { icon: Plane, color: "#3FA7D6" }, // sky blue
  health: { icon: HeartPulse, color: "#D65D6E" }, // rose
  bills: { icon: Zap, color: "#D6A73F" }, // amber
  other: { icon: Tag, color: "#8C8C86" }, // neutral gray
};

function matchCategoryKey(categoryName: string): keyof typeof CATEGORY_STYLES {
  const name = categoryName.toLowerCase();
  if (/food|dinner|lunch|breakfast|restaurant|grocer/.test(name)) return "food";
  if (/transport|cab|taxi|uber|fuel|gas|car/.test(name)) return "transport";
  if (/shop|clothes|retail/.test(name)) return "shopping";
  if (/rent|home|house|utilit|electric|water/.test(name)) return "home";
  if (/movie|entertain|film|game/.test(name)) return "entertainment";
  if (/travel|flight|trip|hotel/.test(name)) return "travel";
  if (/health|medic|doctor|pharmacy/.test(name)) return "health";
  if (/bill|electric|power/.test(name)) return "bills";
  return "other";
}

/**
 * Maps a category's name to a representative icon via keyword matching.
 * Falls back to a generic Tag icon for categories that don't match a known
 * keyword. Shared by every screen that displays a category icon
 * (ExpenseDetailScreen, CreateExpenseScreen, GroupDetailScreen's expense
 * rows) so a category always renders identically wherever it appears -
 * previously this was duplicated per-screen and could drift out of sync.
 */
export function getCategoryIcon(categoryName: string): LucideIcon {
  return CATEGORY_STYLES[matchCategoryKey(categoryName)].icon;
}

/**
 * Fixed accent color for a category's icon chip, e.g. "#E0793C" for food.
 * Pass this as the icon-wrap background (at full or reduced opacity) and
 * use white or the same hue (darkened) for the icon glyph itself - see
 * `getCategoryChipColors` for a ready-made {bg, icon} pair.
 */
export function getCategoryColor(categoryName: string): string {
  return CATEGORY_STYLES[matchCategoryKey(categoryName)].color;
}

/**
 * Convenience helper returning chip colors tuned per theme mode.
 *
 * Light mode: a soft ~20%-opacity tint background with the solid category
 * color as the icon glyph - reads clearly against light surfaces.
 * Dark mode: a solid category-colored background with a white icon glyph -
 * on dark surfaces, a light tint background against a similarly-hued icon
 * loses contrast, so dark mode instead goes full-solid with a white icon
 * to stay legible.
 */
export function getCategoryChipColors(
  categoryName: string,
  mode: "light" | "dark" = "light",
): {
  background: string;
  icon: string;
} {
  const color = getCategoryColor(categoryName);
  if (mode === "dark") {
    return { background: color, icon: "#FFFFFF" };
  }
  return { background: `${color}33`, icon: color }; // 33 = ~20% opacity hex
}
