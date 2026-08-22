import ReceiptDemo from "./ReceiptDemo";
import ScrollLink from "./ScrollLink";
import PlayStoreButton from "./PlayStoreButton";

export default function Hero() {
  return (
    <section className="relative overflow-hidden px-6 pb-24 pt-20 sm:pt-28">
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-0 -z-10 h-[500px] w-[900px] -translate-x-1/2 rounded-full bg-accent/[0.07] blur-[120px]"
      />

      <div className="mx-auto max-w-6xl">
        <div className="mb-8 flex justify-center">
          <div className="inline-flex items-center gap-2 rounded-full border border-line px-3.5 py-1.5 text-xs font-medium text-ink2">
            <span className="h-1.5 w-1.5 rounded-full bg-accent" />
            Live now on Android
          </div>
        </div>

        <h1 className="mx-auto max-w-3xl text-balance text-center font-display text-[2.75rem] font-medium leading-[1.08] tracking-tight text-ink sm:text-6xl">
          Split bills by just
          <br />
          <span className="text-accent">saying it out loud.</span>
        </h1>

        <p className="mx-auto mt-6 max-w-lg text-balance text-center text-lg leading-relaxed text-ink2">
          Splenza is the AI upgrade to Splitwise. Speak a payment, scan a
          receipt, or type it in — it itemizes the bill and works out exactly
          who owes who.
        </p>

        <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
          <PlayStoreButton className="h-14" />
          <ScrollLink
            href="#how-it-works"
            className="inline-flex items-center gap-2 rounded-full border border-line px-6 py-3.5 text-sm font-medium text-ink transition-colors hover:bg-stripe"
          >
            See how it works
          </ScrollLink>
        </div>

        <p className="mt-5 text-center text-xs text-ink3">
          Free to download ·{" "}
          <ScrollLink href="#download" className="underline hover:text-ink2">
            Join the iOS waitlist
          </ScrollLink>
        </p>

        <div className="relative mt-20">
          <ReceiptDemo />
        </div>
      </div>
    </section>
  );
}
