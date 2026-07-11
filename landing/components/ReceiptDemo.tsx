"use client";

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";

const PHRASE =
  "Split ₹1,200 for dinner between Paul, Emma, and Marco — Marco only had a drink, so add ₹200 for him";

type Line = {
  name: string;
  amount: number;
  note?: string;
};

const LINES: Line[] = [
  { name: "Paul", amount: 500 },
  { name: "Emma", amount: 500 },
  { name: "Marco", amount: 200, note: "drink only" },
];

const STAGE_DURATIONS = [2600, 900, 2200, 2600];

export default function ReceiptDemo() {
  const [stage, setStage] = useState(0);
  const [typed, setTyped] = useState("");

  useEffect(() => {
    if (stage !== 0) return;
    setTyped("");
    let i = 0;
    const id = setInterval(() => {
      i++;
      setTyped(PHRASE.slice(0, i));
      if (i >= PHRASE.length) clearInterval(id);
    }, 28);
    return () => clearInterval(id);
  }, [stage]);

  useEffect(() => {
    const t = setTimeout(() => {
      setStage((s) => (s + 1) % 4);
    }, STAGE_DURATIONS[stage]);
    return () => clearTimeout(t);
  }, [stage]);

  return (
    <div className="mx-auto w-full max-w-md select-none">
      <div className="rounded-2xl border border-line bg-surface p-7 shadow-[0_24px_60px_-32px_rgba(17,18,16,0.25)]">
        <div className="mb-5 flex items-center justify-between border-b border-line pb-5">
          <div>
            <p className="font-display text-lg font-medium leading-none text-ink">
              Goa Trip
            </p>
            <p className="mt-1.5 text-xs text-ink3">4 members</p>
          </div>
          <span className="rounded-full bg-accent-soft px-3 py-1 text-[10px] font-medium uppercase tracking-wide text-accent-dark">
            Voice entry
          </span>
        </div>

        <div className="mb-5 flex items-start gap-3 rounded-xl bg-stripe p-4">
          <span className="mt-0.5 flex h-8 w-8 flex-none items-center justify-center rounded-full bg-ink text-bg">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <rect
                x="9"
                y="2"
                width="6"
                height="12"
                rx="3"
                fill="currentColor"
              />
              <path
                d="M5 11a7 7 0 0 0 14 0M12 18v3"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
          </span>
          <div className="min-h-[5.1rem] flex-1 pt-1">
            <p className="font-mono text-[13px] leading-snug text-ink2">
              {stage === 0 ? (
                <>
                  {typed}
                  <span className="animate-blink text-accent">|</span>
                </>
              ) : (
                <span className="text-ink3">{PHRASE}</span>
              )}
            </p>
            {stage >= 1 && <Waveform active={stage === 1} />}
          </div>
        </div>

        <div className="space-y-2">
          <AnimatePresence>
            {stage >= 2 &&
              LINES.map((line, idx) => (
                <motion.div
                  key={line.name}
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{
                    delay: idx * 0.2,
                    duration: 0.35,
                    ease: "easeOut",
                  }}
                  className="flex items-center justify-between rounded-xl border border-line px-4 py-3"
                >
                  <div>
                    <p className="text-sm font-medium text-ink">{line.name}</p>
                    {line.note && (
                      <p className="text-[11px] text-ink3">{line.note}</p>
                    )}
                  </div>
                  <p className="font-mono text-sm font-medium text-accent-dark">
                    ₹{line.amount}
                  </p>
                </motion.div>
              ))}
          </AnimatePresence>

          {stage < 2 && (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="h-[48px] animate-pulse rounded-xl bg-stripe"
                  style={{ animationDelay: `${i * 120}ms` }}
                />
              ))}
            </div>
          )}
        </div>

        <div
          className={`${stage >= 2 ? "mt-5" : "mt-[1.8rem]"} flex items-center justify-between border-t border-line pt-5`}
        >
          <span className="text-xs font-medium uppercase tracking-wide text-ink3">
            Total split
          </span>
          <span className="font-display text-lg font-medium text-ink">
            ₹1,200
          </span>
        </div>
      </div>
    </div>
  );
}

function Waveform({ active }: { active: boolean }) {
  const bars = [4, 10, 6, 14, 8, 16, 6, 11, 5];
  return (
    <div className="mt-2 flex h-4 items-end gap-[3px]">
      {bars.map((h, i) => (
        <span
          key={i}
          className="w-[3px] rounded-full bg-accent/50"
          style={{
            height: active ? undefined : h * 0.4,
            animation: active
              ? `waveform 0.9s ease-in-out ${i * 0.06}s infinite`
              : undefined,
          }}
        />
      ))}
      <style jsx>{`
        @keyframes waveform {
          0%,
          100% {
            height: 4px;
          }
          50% {
            height: 16px;
          }
        }
      `}</style>
    </div>
  );
}
