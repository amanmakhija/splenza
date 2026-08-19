import Image from "next/image";

const shots = [
  { label: "Home ledger", src: "/screenshots/dashboard.png" },
  { label: "Groups", src: "/screenshots/groups.png" },
  { label: "Add expense", src: "/screenshots/add-expense.png" },
  { label: "Add by voice", src: "/screenshots/voice-entry.png" },
  { label: "Group details", src: "/screenshots/group-details.png" },
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
        </div>

        <div className="flex snap-x snap-mandatory gap-5 overflow-x-auto pb-4 sm:gap-7 lg:grid lg:grid-cols-5 lg:overflow-visible lg:pb-0">
          {shots.map((s) => (
            <figure
              key={s.label}
              className="group w-[62vw] flex-shrink-0 snap-start sm:w-[45vw] lg:w-full"
            >
              <div className="relative mx-auto aspect-[468/997] w-full max-w-[220px] overflow-hidden rounded-[1.6rem] border-[6px] border-ink bg-ink shadow-[0_18px_36px_-20px_rgba(17,18,16,0.35)] transition-transform duration-300 group-hover:-translate-y-2">
                <Image
                  src={s.src}
                  alt={s.label}
                  fill
                  sizes="(max-width: 640px) 62vw, (max-width: 1024px) 45vw, 220px"
                  className="object-cover"
                />
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
