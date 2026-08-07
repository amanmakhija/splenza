import Link from "next/link";
import Logo from "./Logo";

export default function Footer() {
  return (
    <footer className="border-t border-line px-6 py-10">
      <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-6 sm:flex-row">
        <Logo />

        <p className="font-mono text-xs text-ink2">
          © {new Date().getFullYear()} Splenza. Made for people who split the
          bill, not the friendship.
        </p>

        <div className="flex items-center gap-5 text-sm text-ink2">
          <Link href="/privacy-policy" className="hover:text-ink">
            Privacy
          </Link>

          <Link href="/terms" className="hover:text-ink">
            Terms
          </Link>

          <a href="mailto:help@splenza.in" className="hover:text-ink">
            Contact
          </a>
        </div>
      </div>
    </footer>
  );
}
