const shots = [
  { label: "Home ledger" },
  { label: "Voice entry" },
  { label: "Receipt scan" },
  { label: "Simplify debts" },
];

export default function ScreenshotShowcase() {
  return (
    <section id="screenshots" className="px-6 py-24">
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 flex flex-wrap items-end justify-between gap-6">
          <div className="max-w-lg">
            <span className="font-mono text-xs uppercase tracking-wider text-ink3">
              Inside the app
            </span>
            <h2 className="mt-3 font-display text-3xl font-medium tracking-tight text-ink sm:text-4xl">
              A closer look at Splenza.
            </h2>
          </div>
          <p className="max-w-xs text-sm text-ink2">
            Real screens are on their way — these frames are placeholders
            until launch.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-5 sm:gap-7 lg:grid-cols-4">
          {shots.map((s, i) => (
            <figure key={s.label} className="group">
              <div className="relative mx-auto aspect-[9/19] w-full max-w-[220px] rounded-[2rem] border-[6px] border-ink bg-stripe p-1.5 shadow-[0_18px_36px_-20px_rgba(17,18,16,0.35)] transition-transform duration-300 group-hover:-translate-y-2">
                <div className="absolute left-1/2 top-2.5 h-1.5 w-14 -translate-x-1/2 rounded-full bg-ink/80" />
                <div className="flex h-full w-full flex-col items-center justify-center gap-3 rounded-[1.5rem] border border-line bg-surface text-ink3">
                  <svg width="30" height="30" viewBox="0 0 24 24" fill="none">
                    <rect x="3" y="4" width="18" height="14" rx="2" stroke="currentColor" strokeWidth="1.6" />
                    <path d="m3 15 4.5-4.5a1.5 1.5 0 0 1 2.1 0L14 15" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                    <circle cx="16" cy="9" r="1.4" stroke="currentColor" strokeWidth="1.6" />
                  </svg>
                  <span className="font-mono text-[11px]">
                    Screenshot {String(i + 1).padStart(2, "0")}
                  </span>
                </div>
              </div>
              <figcaption className="mt-3 text-center text-xs font-medium text-ink2">
                {s.label}
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </section>
  );
}
