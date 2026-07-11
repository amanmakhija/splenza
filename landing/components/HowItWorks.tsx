const steps = [
  {
    n: "01",
    title: "Say it, scan it, or type it",
    body: "\u201cI paid \u20b91,000 for dinner, Himanshu just had juice.\u201d Talk to Splenza like a friend, or upload the receipt photo instead.",
  },
  {
    n: "02",
    title: "AI itemizes the bill",
    body: "Splenza reads the sentence or receipt, works out who ordered what, and drafts the split automatically — no manual math.",
  },
  {
    n: "03",
    title: "Debts simplify themselves",
    body: "Across every group and every trip, Splenza collapses the web of IOUs into the fewest possible payments.",
  },
];

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="px-6 py-24">
      <div className="mx-auto max-w-6xl">
        <div className="mb-16 max-w-lg">
          <span className="font-mono text-xs uppercase tracking-wider text-ink3">
            How it works
          </span>
          <h2 className="mt-3 font-display text-3xl font-medium tracking-tight text-ink sm:text-4xl">
            From a sentence to a settled bill.
          </h2>
        </div>

        <div className="grid gap-12 md:grid-cols-3 md:gap-8">
          {steps.map((s) => (
            <div key={s.n} className="border-t border-line pt-6">
              <span className="font-mono text-xs text-ink3">{s.n}</span>
              <h3 className="mt-3 font-display text-xl font-medium text-ink">
                {s.title}
              </h3>
              <p className="mt-2.5 text-[15px] leading-relaxed text-ink2">
                {s.body}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
