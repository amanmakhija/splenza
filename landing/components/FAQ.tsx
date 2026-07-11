"use client";

import { useState } from "react";
import { faqs } from "@/lib/faq-data";

export default function FAQ() {
  const [open, setOpen] = useState<number | null>(0);

  return (
    <section id="faq" className="px-6 py-24">
      <div className="mx-auto max-w-3xl">
        <div className="mb-12 text-center">
          <span className="font-mono text-xs uppercase tracking-wider text-ink3">
            FAQ
          </span>
          <h2 className="mx-auto mt-3 max-w-md font-display text-3xl font-medium tracking-tight text-ink sm:text-4xl">
            Questions, answered.
          </h2>
        </div>

        <div className="divide-y divide-line rounded-2xl border border-line bg-surface">
          {faqs.map((f, i) => {
            const isOpen = open === i;
            return (
              <div key={f.q}>
                <button
                  onClick={() => setOpen(isOpen ? null : i)}
                  aria-expanded={isOpen}
                  className="flex w-full items-center justify-between gap-4 px-6 py-5 text-left"
                >
                  <span className="font-display text-[1.05rem] font-medium text-ink">
                    {f.q}
                  </span>
                  <span
                    className={`flex h-7 w-7 flex-none items-center justify-center rounded-full border border-line text-ink2 transition-transform duration-300 ${
                      isOpen ? "rotate-45" : ""
                    }`}
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                      <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </span>
                </button>
                <div
                  className="grid overflow-hidden transition-all duration-300 ease-in-out"
                  style={{ gridTemplateRows: isOpen ? "1fr" : "0fr" }}
                >
                  <div className="overflow-hidden">
                    <p className="px-6 pb-5 text-[15px] leading-relaxed text-ink2">
                      {f.a}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
