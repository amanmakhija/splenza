import { AxiosError } from "axios";
import { getApiErrorMessage, getApiErrorCode } from "@/lib/apiClient";

// Helper to build a realistic AxiosError without needing a real network call
function makeAxiosError(overrides: Partial<AxiosError> = {}): AxiosError {
  const err = new AxiosError("Request failed");
  Object.assign(err, overrides);
  return err;
}

jest.mock("@react-native-async-storage/async-storage", () =>
  require("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);
global.IS_REACT_ACT_ENVIRONMENT = true;

describe("getApiErrorMessage", () => {
  it("returns a network-specific message when there is no response at all", () => {
    const err = makeAxiosError({ response: undefined, code: undefined });
    expect(getApiErrorMessage(err)).toBe(
      "Can't reach the server. Please check your internet connection.",
    );
  });

  it("returns a timeout-specific message for ECONNABORTED", () => {
    const err = makeAxiosError({ response: undefined, code: "ECONNABORTED" });
    expect(getApiErrorMessage(err)).toBe(
      "That took too long. Please check your connection and try again.",
    );
  });

  it("prefers the first field error when fieldErrors is present", () => {
    const err = makeAxiosError({
      response: {
        status: 400,
        data: {
          timestamp: "",
          status: 400,
          error: "Bad Request",
          message: "Validation failed",
          path: "/api/v1/auth/signup",
          fieldErrors: { email: "Email already in use", password: "Too short" },
        },
      } as any,
    });
    expect(getApiErrorMessage(err)).toBe("Email already in use");
  });

  it("falls back to body.message when there are no fieldErrors", () => {
    const err = makeAxiosError({
      response: {
        status: 409,
        data: {
          timestamp: "",
          status: 409,
          error: "Conflict",
          message: "Account already exists",
          path: "/api/v1/auth/signup",
        },
      } as any,
    });
    expect(getApiErrorMessage(err)).toBe("Account already exists");
  });

  it("returns a generic 5xx message when the backend gives no message body", () => {
    const err = makeAxiosError({
      response: { status: 500, data: {} } as any,
    });
    expect(getApiErrorMessage(err)).toBe(
      "Something went wrong on our end. Please try again in a moment.",
    );
  });

  it("falls back to the provided default for non-axios errors", () => {
    expect(getApiErrorMessage(new Error("boom"), "Custom fallback")).toBe(
      "Custom fallback",
    );
  });

  it("uses the default fallback text when none is provided", () => {
    expect(getApiErrorMessage(new Error("boom"))).toBe(
      "Something went wrong. Please try again.",
    );
  });
});

describe("getApiErrorCode", () => {
  it("extracts a machine-readable code from the response body when present", () => {
    const err = makeAxiosError({
      response: { data: { code: "EMAIL_NOT_VERIFIED" } } as any,
    });
    expect(getApiErrorCode(err)).toBe("EMAIL_NOT_VERIFIED");
  });

  it("returns undefined for non-axios errors", () => {
    expect(getApiErrorCode(new Error("boom"))).toBeUndefined();
  });
});
