"use client";

import { useState } from "react";

const WAITLIST_API_URL = "https://api.splenza.in/api/v1/waitlist";

type Status = "idle" | "loading" | "success" | "error";

export default function CTASection() {
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email) return;

    setStatus("loading");
    setMessage("");

    try {
      const res = await fetch(WAITLIST_API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });

      const data = await res.json().catch(() => null);

      if (!res.ok) {
        setStatus("error");
        setMessage(data?.message || "Something went wrong. Please try again.");
        return;
      }

      setStatus("success");
      setMessage(data?.message || "You have joined the waitlist.");
    } catch {
      setStatus("error");
      setMessage("Couldn't reach the server. Please try again.");
    }
  }

  return (
    <section id="download" className="px-6 py-24">
      <div className="mx-auto max-w-5xl overflow-hidden rounded-[28px] bg-ink px-8 py-16 text-center sm:px-16">
        <span className="font-mono text-xs font-medium uppercase tracking-wider text-accent-soft">
          Coming first to Android
        </span>
        <h2 className="mx-auto mt-4 max-w-xl text-balance font-display text-3xl font-medium tracking-tight text-bg sm:text-4xl">
          Be first to split smarter.
        </h2>
        <p className="mx-auto mt-4 max-w-md text-[15px] leading-relaxed text-bg/70">
          Join the waitlist for the Android launch. iOS follows soon after —
          we&apos;ll let you know the moment it&apos;s ready.
        </p>

        <form
          className="mx-auto mt-8 flex max-w-md flex-col gap-3 sm:flex-row"
          onSubmit={handleSubmit}
        >
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={status === "loading" || status === "success"}
            placeholder="you@email.com"
            className="w-full flex-1 rounded-full border border-bg/15 bg-bg/5 px-5 py-3.5 text-sm text-bg placeholder:text-bg/40 focus:border-accent focus:outline-none disabled:opacity-60"
          />
          <button
            type="submit"
            disabled={status === "loading" || status === "success"}
            className="inline-flex items-center justify-center gap-2 rounded-full bg-accent px-6 py-3.5 text-sm font-semibold text-bg transition-transform hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
          >
            {status === "loading" ? "Joining…" : "Join waitlist"}
          </button>
        </form>

        {message && (
          <p
            className={`mt-4 text-sm font-medium ${
              status === "error" ? "text-red-300" : "text-accent-soft"
            }`}
            role="status"
          >
            {message}
          </p>
        )}

        <p className="mt-5 font-mono text-[11px] text-bg/40">
          No spam. One email when Splenza is live.
        </p>
      </div>
    </section>
  );
}
