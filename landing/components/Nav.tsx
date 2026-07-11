import Logo from "./Logo";
import ScrollLink from "./ScrollLink";

const links = [
  { href: "#how-it-works", label: "How it works" },
  { href: "#features", label: "Features" },
  { href: "#faq", label: "FAQ" },
] as const;

export default function Nav() {
  return (
    <header className="sticky top-0 z-50 border-b border-line/70 bg-bg/80 backdrop-blur-md">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Logo />
        <nav className="hidden items-center gap-8 md:flex">
          {links.map((l) => (
            <ScrollLink
              key={l.href}
              href={l.href}
              className="text-sm text-ink2 transition-colors hover:text-ink"
            >
              {l.label}
            </ScrollLink>
          ))}
        </nav>
        <ScrollLink
          href="#download"
          className="rounded-full bg-ink px-5 py-2.5 text-sm font-medium text-bg transition-opacity hover:opacity-85"
        >
          Get early access
        </ScrollLink>
      </div>
    </header>
  );
}
