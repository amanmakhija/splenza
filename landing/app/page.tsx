import Nav from "@/components/Nav";
import Hero from "@/components/Hero";
import MarqueeStrip from "@/components/MarqueeStrip";
import HowItWorks from "@/components/HowItWorks";
import Features from "@/components/Features";
import Comparison from "@/components/Comparison";
import ScreenshotShowcase from "@/components/ScreenshotShowcase";
import FAQ from "@/components/FAQ";
import FAQSchema from "@/components/FAQSchema";
import CTASection from "@/components/CTASection";
import Footer from "@/components/Footer";
import StickyMobileCTA from "@/components/StickyMobileCTA";

export default function Home() {
  return (
    <main className="pb-20 sm:pb-0">
      <FAQSchema />
      <Nav />
      <Hero />
      <MarqueeStrip />
      <HowItWorks />
      <Features />
      <Comparison />
      <ScreenshotShowcase />
      <FAQ />
      <CTASection />
      <Footer />
      <StickyMobileCTA />
    </main>
  );
}
