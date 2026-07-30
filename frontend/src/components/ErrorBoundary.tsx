import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { reportError } from "@/lib/crashReporting";

interface Props {
  children: React.ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Catches uncaught render/lifecycle errors anywhere below it in the tree and shows
 * a recovery screen instead of a blank white screen / full app crash.
 *
 * NOTE: This only catches errors during React's render phase (render methods,
 * lifecycle methods, constructors of the tree below it). It does NOT catch:
 *   - Errors in event handlers (use try/catch there, or a global handler below)
 *   - Errors in async code (promises, setTimeout, etc.)
 *   - Errors thrown in the ErrorBoundary itself
 * See setupGlobalErrorHandlers() in this same file for those cases.
 */
export class ErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    reportError(error, `React render error: ${info.componentStack}`);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <View style={styles.container}>
          <Text style={styles.title}>Something went wrong</Text>
          <Text style={styles.message}>
            We hit an unexpected error. Please try again — if this keeps
            happening, restarting the app usually helps.
          </Text>
          {__DEV__ && this.state.error ? (
            <Text style={styles.debugText}>{this.state.error.toString()}</Text>
          ) : null}
          <TouchableOpacity style={styles.button} onPress={this.handleReset}>
            <Text style={styles.buttonText}>Try Again</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FAFAF8",
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: "700",
    color: "#1A1A1A",
    marginBottom: 8,
    textAlign: "center",
  },
  message: {
    fontSize: 15,
    color: "#666666",
    textAlign: "center",
    marginBottom: 24,
    lineHeight: 22,
  },
  debugText: {
    fontSize: 12,
    color: "#B00020",
    marginBottom: 24,
    textAlign: "left",
  },
  button: {
    backgroundColor: "#4B4FE0",
    paddingVertical: 12,
    paddingHorizontal: 32,
    borderRadius: 10,
  },
  buttonText: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "600",
  },
});
