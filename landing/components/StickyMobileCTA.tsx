import PlayStoreButton from "./PlayStoreButton";

export default function StickyMobileCTA() {
  return (
    <div className="fixed inset-x-0 bottom-0 z-50 border-t border-line bg-bg/95 px-4 py-3 backdrop-blur-md sm:hidden">
      <PlayStoreButton className="w-full justify-center shadow-lg" />
    </div>
  );
}
