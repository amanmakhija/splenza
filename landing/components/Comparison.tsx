const rows = [
  { label: "Add an expense", old: "Open app, tap add, type amount, select people, adjust shares", new: "Say it in one sentence" },
  { label: "Itemize a group bill", old: "Manually split every item by hand", new: "AI reads the receipt for you" },
  { label: "Settle up with 5 people", old: "5+ separate payments back and forth", new: "Simplified to the fewest payments" },
  { label: "Remembering who paid what", old: "Scroll through history", new: "Ask, and Splenza tells you" },
];

export default function Comparison() {
  return (
    <section className="px-6 py-24">
      <div className="mx-auto max-w-5xl">
        <div className="mb-14 text-center">
          <span className="font-mono text-xs uppercase tracking-wider text-ink3">
            Why switch
          </span>
          <h2 className="mx-auto mt-3 max-w-lg font-display text-3xl font-medium tracking-tight text-ink sm:text-4xl">
            Everything you&apos;re used to. None of the busywork.
          </h2>
        </div>

        <div className="overflow-hidden rounded-2xl border border-line bg-surface">
          <div className="grid grid-cols-[1.1fr_1fr_1fr] border-b border-line bg-stripe text-xs font-medium uppercase tracking-wide text-ink3">
            <div className="px-5 py-4 sm:px-7">Task</div>
            <div className="px-5 py-4 sm:px-7">Old-school apps</div>
            <div className="flex items-center gap-2 px-5 py-4 text-accent-dark sm:px-7">
              Splenza
            </div>
          </div>
          {rows.map((r, i) => (
            <div
              key={r.label}
              className={`grid grid-cols-[1.1fr_1fr_1fr] text-sm ${
                i !== rows.length - 1 ? "border-b border-line" : ""
              }`}
            >
              <div className="px-5 py-5 font-medium text-ink sm:px-7">
                {r.label}
              </div>
              <div className="flex items-start gap-2 px-5 py-5 text-ink2 sm:px-7">
                <span className="mt-0.5 text-ink2/50">–</span>
                <span>{r.old}</span>
              </div>
              <div className="flex items-start gap-2 bg-accent-soft/40 px-5 py-5 text-ink sm:px-7">
                <span className="mt-0.5 flex h-4 w-4 flex-none items-center justify-center rounded-full bg-accent text-bg">
                  <svg width="9" height="9" viewBox="0 0 24 24" fill="none">
                    <path d="M5 13l4 4L19 7" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </span>
                <span className="font-medium">{r.new}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
