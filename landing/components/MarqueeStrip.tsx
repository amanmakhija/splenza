const phrases = [
  "“Split ₹1,200 for dinner”",
  "Split instantly",
  "“Scan this receipt”",
  "Itemized in seconds",
  "“Marco only had a drink”",
  "Debts simplified",
];

export default function MarqueeStrip() {
  const loop = [...phrases, ...phrases];
  return (
    <div className="relative overflow-hidden border-y border-line py-4">
      <div className="flex w-max animate-marquee gap-12">
        {[...loop, ...loop].map((p, i) => (
          <span
            key={i}
            className="flex items-center gap-12 font-mono text-xs uppercase tracking-wide text-ink3"
          >
            {p}
            <span className="h-1 w-1 rounded-full bg-line" />
          </span>
        ))}
      </div>
    </div>
  );
}
