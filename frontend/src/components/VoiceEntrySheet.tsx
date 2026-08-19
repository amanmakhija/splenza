import React, { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  Linking,
} from "react-native";
import {
  useAudioRecorder,
  useAudioRecorderState,
  RecordingPresets,
  requestRecordingPermissionsAsync,
  getRecordingPermissionsAsync,
  setAudioModeAsync,
} from "expo-audio";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
  Easing,
} from "react-native-reanimated";
import { Mic, Square, X } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { AppModal } from "@/components/AppModal";
import {
  submitVoiceExpense,
  InsufficientCreditsError,
} from "@/lib/voiceExpense";
import { VoiceExpenseResult } from "@/types/api";

type Stage = "idle" | "recording" | "processing" | "permission-denied";

interface VoiceEntrySheetProps {
  visible: boolean;
  groupId: string;
  onClose: () => void;
  onResult: (result: VoiceExpenseResult) => void;
  onInsufficientCredits: (message: string) => void;
  onError: (message: string) => void;
}

function formatDuration(ms: number) {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/**
 * Handles the whole voice-expense capture flow: mic permission, recording
 * with a live timer, uploading, and waiting on the parse result - then
 * hands the result back to the caller (`CreateExpenseScreen`) to actually
 * apply to the form. This component never touches expense state itself;
 * it's purely "get a recording turned into a VoiceExpenseResult."
 */
export function VoiceEntrySheet({
  visible,
  groupId,
  onClose,
  onResult,
  onInsufficientCredits,
  onError,
}: VoiceEntrySheetProps) {
  const { theme } = useAppTheme();
  const [stage, setStage] = useState<Stage>("idle");
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder, 200);
  const pulse = useSharedValue(1);

  useEffect(() => {
    if (stage === "recording") {
      pulse.value = withRepeat(
        withTiming(1.25, { duration: 700, easing: Easing.inOut(Easing.ease) }),
        -1,
        true,
      );
    } else {
      pulse.value = withTiming(1, { duration: 150 });
    }
  }, [stage, pulse]);

  const pulseStyle = useAnimatedStyle(() => ({
    transform: [{ scale: pulse.value }],
  }));

  // Reset to a clean idle state every time the sheet is (re)opened.
  useEffect(() => {
    if (visible) setStage("idle");
  }, [visible]);

  const startRecording = useCallback(async () => {
    const existing = await getRecordingPermissionsAsync();
    let granted = existing.granted;
    if (!granted) {
      const result = await requestRecordingPermissionsAsync();
      granted = result.granted;
    }
    if (!granted) {
      setStage("permission-denied");
      return;
    }

    try {
      await setAudioModeAsync({
        allowsRecording: true,
        playsInSilentMode: true,
      });
      await recorder.prepareToRecordAsync();
      recorder.record();
      setStage("recording");
    } catch {
      onError("Couldn't start recording. Please try again.");
    }
  }, [recorder, onError]);

  const cancelRecording = useCallback(async () => {
    try {
      if (recorderState.isRecording) await recorder.stop();
    } catch {
      // Ignore - we're discarding this recording anyway.
    }
    onClose();
  }, [recorder, recorderState.isRecording, onClose]);

  const finishRecording = useCallback(async () => {
    try {
      await recorder.stop();
    } catch {
      onError("Something went wrong with the recording. Please try again.");
      return;
    }

    const uri = recorder.uri;
    if (!uri) {
      onError("Something went wrong with the recording. Please try again.");
      return;
    }

    setStage("processing");
    try {
      const result = await submitVoiceExpense(uri, groupId);
      onResult(result);
    } catch (err) {
      if (err instanceof InsufficientCreditsError) {
        onInsufficientCredits(err.message);
      } else {
        onError(err instanceof Error ? err.message : "Something went wrong.");
      }
    }
  }, [recorder, groupId, onResult, onInsufficientCredits, onError]);

  return (
    <AppModal visible={visible} onClose={cancelRecording} scrollable={false}>
      <View style={styles.wrap}>
        {stage === "permission-denied" ? (
          <>
            <Text style={[styles.title, { color: theme.textPrimary }]}>
              Microphone access needed
            </Text>
            <Text style={[styles.subtitle, { color: theme.textMuted }]}>
              To add an expense by voice, allow Splenza to use your microphone
              in your device settings.
            </Text>
            <Pressable
              onPress={() => Linking.openSettings()}
              style={[
                styles.settingsButton,
                { backgroundColor: theme.primary },
              ]}
            >
              <Text style={styles.settingsButtonText}>Open Settings</Text>
            </Pressable>
          </>
        ) : stage === "processing" ? (
          <>
            <ActivityIndicator size="large" color={theme.primary} />
            <Text
              style={[
                styles.title,
                { color: theme.textPrimary, marginTop: 16 },
              ]}
            >
              Listening…
            </Text>
            <Text style={[styles.subtitle, { color: theme.textMuted }]}>
              Turning that into an expense.
            </Text>
          </>
        ) : (
          <>
            <Text style={[styles.title, { color: theme.textPrimary }]}>
              {stage === "recording" ? "Listening…" : "Add expense by voice"}
            </Text>
            <Text style={[styles.subtitle, { color: theme.textMuted }]}>
              {stage === "recording"
                ? "Tap the square to finish."
                : `Try something like "Dinner ₹1200 split between Paul and Emma".`}
            </Text>

            {stage === "recording" ? (
              <Text style={[styles.timer, { color: theme.textPrimary }]}>
                {formatDuration(recorderState.durationMillis)}
              </Text>
            ) : null}

            <View style={styles.micRow}>
              <Animated.View
                style={[
                  styles.micGlow,
                  { backgroundColor: theme.primaryContainer },
                  stage === "recording" ? pulseStyle : undefined,
                ]}
              />
              <Pressable
                onPress={
                  stage === "recording" ? finishRecording : startRecording
                }
                accessibilityRole="button"
                accessibilityLabel={
                  stage === "recording" ? "Stop recording" : "Start recording"
                }
                style={[styles.micButton, { backgroundColor: theme.primary }]}
              >
                {stage === "recording" ? (
                  <Square size={26} color="#fff" fill="#fff" />
                ) : (
                  <Mic size={28} color="#fff" />
                )}
              </Pressable>
            </View>

            <Pressable
              onPress={cancelRecording}
              style={styles.cancelButton}
              accessibilityRole="button"
              accessibilityLabel="Cancel"
            >
              <X size={14} color={theme.textMuted} />
              <Text style={[styles.cancelText, { color: theme.textMuted }]}>
                Cancel
              </Text>
            </Pressable>
          </>
        )}
      </View>
    </AppModal>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: "center", paddingVertical: 12, paddingHorizontal: 8 },
  title: { fontSize: 17, fontWeight: "800", textAlign: "center" },
  subtitle: {
    fontSize: 13,
    textAlign: "center",
    marginTop: 6,
    lineHeight: 19,
    paddingHorizontal: 12,
  },
  timer: {
    fontSize: 28,
    fontWeight: "700",
    marginTop: 20,
    fontVariant: ["tabular-nums"],
  },
  micRow: {
    marginTop: 24,
    marginBottom: 8,
    width: 96,
    height: 96,
    alignItems: "center",
    justifyContent: "center",
  },
  micGlow: {
    position: "absolute",
    width: 96,
    height: 96,
    borderRadius: 48,
  },
  micButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    alignItems: "center",
    justifyContent: "center",
  },
  cancelButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: 20,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  cancelText: { fontSize: 13, fontWeight: "600" },
  settingsButton: {
    marginTop: 20,
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 24,
  },
  settingsButtonText: { color: "#fff", fontWeight: "700", fontSize: 14 },
});
