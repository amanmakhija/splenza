import React, { useState } from "react";
import {
  Pressable,
  Text,
  StyleSheet,
  StyleProp,
  ViewStyle,
} from "react-native";
import { Plus } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { AddExpensePickerSheet } from "@/components/AddExpensePickerSheet";

interface AddExpenseFabProps {
  /** Position override - e.g. `{ bottom: 92 }` to stack above another FAB
   * already occupying the default bottom-right spot. */
  style?: StyleProp<ViewStyle>;
}

/**
 * Extended (icon + label) floating "add expense" button for screens that
 * don't already have a specific group in context (Dashboard, the Groups
 * list, the Friends list) - tapping it opens a picker to choose which
 * group/friend the new expense belongs to, then hands off to the existing
 * CreateExpense screen. Labeled rather than icon-only so it reads
 * unambiguously as "add an expense" rather than being mistaken for some
 * other "+" action on a given screen (e.g. "create group").
 *
 * GroupDetailScreen has its own icon-only version of this that skips the
 * picker entirely (it already knows which group), so this isn't used there.
 */
export function AddExpenseFab({ style }: AddExpenseFabProps) {
  const { theme } = useAppTheme();
  const [pickerVisible, setPickerVisible] = useState(false);

  return (
    <>
      <Pressable
        onPress={() => setPickerVisible(true)}
        accessibilityLabel="Add expense"
        accessibilityRole="button"
        style={[styles.fab, { backgroundColor: theme.primary }, style]}
      >
        <Plus color="#fff" size={20} />
        <Text style={styles.label}>Add expense</Text>
      </Pressable>
      <AddExpensePickerSheet
        visible={pickerVisible}
        onClose={() => setPickerVisible(false)}
      />
    </>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: "absolute",
    right: 20,
    bottom: 24,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 18,
    height: 52,
    borderRadius: 26,
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  label: { color: "#fff", fontWeight: "700", fontSize: 15 },
});
