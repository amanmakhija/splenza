import type { Metadata } from "next";
import { Space_Grotesk, Inter, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";

const spaceGrotesk = Space_Grotesk({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-space-grotesk",
  display: "swap",
});

const inter = Inter({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-inter",
  display: "swap",
});

const plexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-plex-mono",
  display: "swap",
});

const SITE_URL = "https://getsplenza.in";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default:
      "Splenza — Split Bills by Just Saying It | AI Expense Splitting App",
    template: "%s | Splenza",
  },
  description:
    "Splenza is the AI-powered Splitwise alternative. Speak a payment, scan a receipt, or type it in — Splenza itemizes the bill and simplifies who owes who. Free waitlist open now for Android, iOS coming soon.",
  keywords: [
    "split bills app",
    "expense splitting app",
    "Splitwise alternative",
    "AI bill splitter",
    "voice expense tracker",
    "split expenses with friends",
    "group expense app",
    "receipt scanner split bill",
    "Splenza",
  ],
  authors: [{ name: "Splenza" }],
  creator: "Splenza",
  applicationName: "Splenza",
  category: "Finance",
  alternates: {
    canonical: "/",
  },
  openGraph: {
    type: "website",
    url: SITE_URL,
    siteName: "Splenza",
    title: "Splenza — Split Bills by Just Saying It",
    description:
      "The AI-powered way to split expenses with friends. Speak it, scan it, or type it — Splenza handles the math.",
    images: [
      {
        url: "/logo-mark.svg",
        width: 1200,
        height: 630,
        alt: "Splenza — AI expense splitting app",
      },
    ],
    locale: "en_US",
  },
  twitter: {
    card: "summary_large_image",
    title: "Splenza — Split Bills by Just Saying It",
    description:
      "The AI-powered way to split expenses with friends. Speak it, scan it, or type it — Splenza handles the math.",
    images: ["/logo-mark.svg"],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
    },
  },
  icons: {
    icon: "/favicon.svg",
    apple: "/apple-touch-icon.svg",
  },
};

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "Splenza",
  applicationCategory: "FinanceApplication",
  operatingSystem: "Android, iOS",
  description:
    "Splenza is an AI-powered bill-splitting app. Speak a payment, scan a receipt, or type it in, and Splenza itemizes and simplifies group expenses.",
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
  },
  url: SITE_URL,
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <head>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      </head>
      <body
        className={`${spaceGrotesk.variable} ${inter.variable} ${plexMono.variable} font-body bg-bg text-ink antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
