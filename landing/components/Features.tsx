function IconMic() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <rect x="9" y="2" width="6" height="12" rx="3" stroke="currentColor" strokeWidth="2" />
      <path d="M5 11a7 7 0 0 0 14 0M12 18v3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
function IconReceipt() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path d="M6 2h12v20l-3-2-3 2-3-2-3 2V2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="M9 8h6M9 12h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
function IconArrows() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path d="M4 8h13M17 8l-3-3M17 8l-3 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M20 16H7M7 16l3 3M7 16l3-3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
function IconGroup() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <circle cx="9" cy="8" r="3" stroke="currentColor" strokeWidth="2" />
      <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" stroke="currentColor" strokeWidth="2" />
      <circle cx="17" cy="7" r="2.4" stroke="currentColor" strokeWidth="2" />
      <path d="M15.5 14.2c2.6.4 4.5 2.7 4.5 5.8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
function IconBell() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path d="M6 10a6 6 0 1 1 12 0c0 4 1.5 5.5 1.5 5.5H4.5S6 14 6 10Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="M10 19a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}
function IconSpark() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

const features = [
  {
    icon: IconMic,
    title: "Voice entry",
    body:
      "\u201cI paid for dinner, about \u20b91,000, Himanshu just had juice.\u201d Splenza understands casual, natural sentences and does the math.",
    tone: "emerald" as const,
    span: "md:col-span-2",
  },
  {
    icon: IconReceipt,
    title: "Receipt scan",
    body: "Photograph the bill. Splenza reads every line item and builds the record for you.",
    tone: "violet" as const,
  },
  {
    icon: IconArrows,
    title: "Simplify debts",
    body: "Turns a tangle of who-owes-who into the smallest set of payments across the whole group.",
    tone: "emerald" as const,
  },
  {
    icon: IconGroup,
    title: "Group ledgers",
    body: "Trips, flats, and friend circles each get their own running tally, always in sync.",
    tone: "emerald" as const,
  },
  {
    icon: IconBell,
    title: "Gentle reminders",
    body: "Splenza nudges the right person at the right time — never awkward, always on time.",
    tone: "violet" as const,
  },
  {
    icon: IconSpark,
    title: "More AI, every update",
    body: "Spending insights, smart categorization, and predictive splitting are on the way.",
    tone: "emerald" as const,
    span: "md:col-span-2",
  },
];

const tones = {
  violet: "bg-stripe text-ink",
  amber: "bg-stripe text-ink",
  emerald: "bg-accent-soft text-accent-dark",
};

export default function Features() {
  return (
    <section id="features" className="px-6 py-24">
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 max-w-lg">
          <span className="font-mono text-xs uppercase tracking-wider text-ink3">
            Features
          </span>
          <h2 className="mt-3 font-display text-3xl font-medium tracking-tight text-ink sm:text-4xl">
            Everything Splitwise does. Plus a brain.
          </h2>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          {features.map((f) => (
            <div
              key={f.title}
              className={`group rounded-2xl border border-line bg-surface p-6 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_40px_-24px_rgba(17,18,16,0.2)] ${f.span ?? ""}`}
            >
              <div
                className={`mb-4 flex h-10 w-10 items-center justify-center rounded-xl ${tones[f.tone]}`}
              >
                <f.icon />
              </div>
              <h3 className="font-display text-lg font-semibold text-ink">
                {f.title}
              </h3>
              <p className="mt-2 text-[14.5px] leading-relaxed text-ink2">
                {f.body}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
