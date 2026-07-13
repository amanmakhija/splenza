"use client";

import { useState } from "react";

const RESET_PASSWORD_API_URL =
  "https://api.getsplenza.in/api/v1/auth/reset-password";

type Status = "idle" | "loading" | "success" | "error";

export default function ResetPasswordForm({ token }: { token?: string }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!token) {
      setStatus("error");
      setMessage("This reset link is missing or invalid.");
      return;
    }
    if (password.length < 8) {
      setStatus("error");
      setMessage("Password must be at least 8 characters.");
      return;
    }
    if (password !== confirm) {
      setStatus("error");
      setMessage("Passwords don't match.");
      return;
    }

    setStatus("loading");
    setMessage("");

    try {
      const res = await fetch(RESET_PASSWORD_API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, password }),
      });
      const data = await res.json().catch(() => null);

      if (!res.ok) {
        setStatus("error");
        setMessage(data?.message || "That reset link may have expired.");
        return;
      }

      setStatus("success");
      setMessage(data?.message || "Your password has been reset.");
    } catch {
      setStatus("error");
      setMessage("Couldn't reach the server. Please try again.");
    }
  }

  if (status === "success") {
    return (
      <div className="rounded-2xl border border-line bg-surface p-7 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M5 13l4 4L19 7"
              stroke="#3A3ECB"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </div>
        <h1 className="font-display text-xl font-medium text-ink">
          Password updated
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-ink2">{message}</p>
        <p className="mt-4 text-sm text-ink3">
          You can now sign in with your new password in the Splenza app.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-line bg-surface p-7">
      <h1 className="font-display text-2xl font-medium text-ink">
        Reset your password
      </h1>
      <p className="mt-2 text-sm leading-relaxed text-ink2">
        Choose a new password for your Splenza account.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 space-y-3">
        <input
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          disabled={status === "loading"}
          placeholder="New password"
          className="w-full rounded-full border border-line bg-bg px-5 py-3.5 text-sm text-ink placeholder:text-ink3 focus:border-accent focus:outline-none disabled:opacity-60"
        />
        <input
          type="password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          disabled={status === "loading"}
          placeholder="Confirm new password"
          className="w-full rounded-full border border-line bg-bg px-5 py-3.5 text-sm text-ink placeholder:text-ink3 focus:border-accent focus:outline-none disabled:opacity-60"
        />
        <button
          type="submit"
          disabled={status === "loading"}
          className="w-full rounded-full bg-ink px-6 py-3.5 text-sm font-medium text-bg transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {status === "loading" ? "Updating…" : "Update password"}
        </button>
      </form>

      {message && status === "error" && (
        <p className="mt-4 text-sm font-medium text-red-500" role="status">
          {message}
        </p>
      )}
    </div>
  );
}
