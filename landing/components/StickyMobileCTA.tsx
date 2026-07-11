import ScrollLink from "./ScrollLink";

export default function StickyMobileCTA() {
  return (
    <div className="fixed inset-x-0 bottom-0 z-50 border-t border-line bg-bg/95 px-4 py-3 backdrop-blur-md sm:hidden">
      <ScrollLink
        href="#download"
        className="flex w-full items-center justify-center gap-2 rounded-full bg-ink px-5 py-3.5 text-sm font-semibold text-bg shadow-lg"
      >
        Get early access — it&apos;s free
      </ScrollLink>
    </div>
  );
}
