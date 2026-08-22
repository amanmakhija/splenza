const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.splenza.app";

export default function PlayStoreButton({
  className = "",
}: {
  className?: string;
}) {
  return (
    <a
      href={PLAY_STORE_URL}
      target="_blank"
      rel="noopener noreferrer"
      className={`inline-flex items-center gap-2.5 rounded-full bg-ink px-6 py-3.5 text-sm font-medium text-bg transition-opacity hover:opacity-85 ${className}`}
    >
      <svg
        viewBox="0 0 512 512"
        className="h-5 w-5 flex-shrink-0"
        aria-hidden="true"
      >
        <path
          fill="#00D9A6"
          d="M325.3 234.3L104.6 13.6c-6-3.5-12.8-4.9-19.4-4.3L272.2 256 85.2 502.7c6.6.6 13.4-.8 19.4-4.3l220.7-220.7 -.1.1z"
        />
        <path
          fill="#FFCE00"
          d="M411.7 208.3l-86.4-50.1-99.1 97.8 99.1 97.8 86.6-50.2c25.6-14.9 25.6-51.8-.2-95.3z"
        />
        <path
          fill="#00D9A6"
          d="M85.2 9.3C81 13.6 78 19.6 78 27.1v457.8c0 7.5 2.8 13.5 7.2 17.8L272.2 256 85.2 9.3z"
        />
        <path
          fill="#FF3D00"
          d="M104.6 498.4l220.7-220.6-45.1-44.7L85.2 502.7c6.4.5 13.2-.7 19.4-4.3z"
        />
      </svg>
      <span className="flex flex-col items-start leading-none">
        <span className="text-[10px] font-normal text-bg/70">Download on</span>
        <span className="text-sm font-semibold">Google Play</span>
      </span>
    </a>
  );
}
