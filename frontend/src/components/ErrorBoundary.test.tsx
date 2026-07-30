import React from "react";
import { Text } from "react-native";
import { render, screen, fireEvent } from "@testing-library/react-native";
import { ErrorBoundary } from "@/components/ErrorBoundary";

jest.mock("@react-native-firebase/crashlytics", () => ({
  getCrashlytics: jest.fn(() => ({})),
  log: jest.fn(),
  recordError: jest.fn(),
  setUserId: jest.fn(),
  setCrashlyticsCollectionEnabled: jest.fn(),
}));

jest.mock("@react-native-firebase/app", () => ({
  getApp: jest.fn(() => ({})),
}));

// Jest/React log expected errors to console during these tests since we're
// intentionally throwing — silence just for this file so test output stays readable.
const originalError = console.error;
beforeAll(() => {
  console.error = jest.fn();
});
afterAll(() => {
  console.error = originalError;
});

function Bomb(): React.ReactElement {
  throw new Error("boom");
}

function Safe() {
  return <Text>All good</Text>;
}

describe("ErrorBoundary", () => {
  it("renders children normally when nothing throws", () => {
    render(
      <ErrorBoundary>
        <Safe />
      </ErrorBoundary>,
    );
    expect(screen.getByText("All good")).toBeTruthy();
  });

  it("shows the fallback UI when a child throws during render", () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );
    expect(screen.getByText("Something went wrong")).toBeTruthy();
    expect(screen.getByText("Try Again")).toBeTruthy();
  });

  it("does not render the crashed child's output alongside the fallback", () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );
    expect(screen.queryByText("All good")).toBeNull();
  });

  it("resets and re-renders children after Try Again, once the error condition clears", () => {
    let shouldThrow = true;
    function Flaky() {
      if (shouldThrow) throw new Error("boom");
      return <Text>Recovered</Text>;
    }

    render(
      <ErrorBoundary>
        <Flaky />
      </ErrorBoundary>,
    );
    expect(screen.getByText("Something went wrong")).toBeTruthy();

    shouldThrow = false;
    fireEvent.press(screen.getByText("Try Again"));

    expect(screen.getByText("Recovered")).toBeTruthy();
  });
});
