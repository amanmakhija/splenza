import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { CircleCheckBig } from "lucide-react-native";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import Animated, {
  FadeIn,
  FadeInDown,
  FadeInUp,
  ZoomIn,
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { Button } from "@/components/Button";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useAppTheme } from "@/theme/ThemeContext";
import { AuthStackParamList } from "@/navigation/types";

type Props = NativeStackScreenProps<AuthStackParamList, "PasswordResetSuccess">;

export function PasswordResetSuccessScreen({ navigation }: Props) {
  const { theme } = useAppTheme();
  const scale = useSharedValue(0.6);

  React.useEffect(() => {
    scale.value = withSpring(1, {
      damping: 9,
      stiffness: 140,
    });
  }, []);

  const iconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <SafeAreaView
      style={[
        styles.container,
        {
          backgroundColor: theme.background,
        },
      ]}
    >
      <View style={styles.topRow}>
        <View />
        <ThemeToggle />
      </View>

      <View style={styles.content}>
        <Animated.View
          entering={ZoomIn.duration(500)}
          style={[
            styles.iconContainer,
            iconStyle,
            {
              backgroundColor: theme.success + "15",
            },
          ]}
        >
          <Animated.View entering={FadeIn.delay(200)}>
            <CircleCheckBig size={52} color={theme.success} />
          </Animated.View>
        </Animated.View>

        <Animated.Text
          entering={FadeInUp.delay(250)}
          style={[
            styles.title,
            {
              color: theme.textPrimary,
            },
          ]}
        >
          Password Updated
        </Animated.Text>

        <Animated.Text
          entering={FadeInUp.delay(350)}
          style={[
            styles.subtitle,
            {
              color: theme.textSecondary,
            },
          ]}
        >
          Your password has been updated successfully.
        </Animated.Text>

        <Animated.Text
          entering={FadeInUp.delay(450)}
          style={[
            styles.description,
            {
              color: theme.textMuted,
            },
          ]}
        >
          You can now log in using your new password.
        </Animated.Text>

        <Animated.View entering={FadeInDown.delay(650)}>
          <Button
            title="Continue to Login"
            onPress={() =>
              navigation.reset({
                index: 0,
                routes: [{ name: "Login" }],
              })
            }
          />
        </Animated.View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  topRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: 24,
    paddingTop: 8,
  },

  content: {
    flex: 1,
    justifyContent: "center",
    paddingHorizontal: 28,
  },

  iconContainer: {
    width: 120,
    height: 120,
    borderRadius: 60,
    justifyContent: "center",
    alignItems: "center",
    alignSelf: "center",
    marginBottom: 36,
    shadowColor: "#22C55E",
    shadowOpacity: 0.18,
    shadowRadius: 20,
    shadowOffset: {
      width: 0,
      height: 10,
    },
    elevation: 10,
  },

  title: {
    textAlign: "center",
    fontSize: 28,
    fontWeight: "800",
  },

  subtitle: {
    textAlign: "center",
    fontSize: 16,
    marginTop: 14,
  },

  description: {
    textAlign: "center",
    fontSize: 14,
    lineHeight: 22,
    marginTop: 12,
    marginBottom: 42,
  },
});
