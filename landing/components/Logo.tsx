export default function Logo({ className = "" }: { className?: string }) {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <svg width="24" height="24" viewBox="0 0 100 100" fill="none" aria-hidden="true">
        <path
          d="M16.67,50 C16.67,31.67 31.67,16.67 50,16.67 C62.5,16.67 73.33,23.75 79.17,34.17"
          stroke="#4B4FE0"
          strokeWidth="10.5"
          strokeLinecap="round"
          fill="none"
        />
        <path
          d="M83.33,50 C83.33,68.33 68.33,83.33 50,83.33 C37.5,83.33 26.67,76.25 20.83,65.83"
          stroke="#4B4FE0"
          strokeWidth="10.5"
          strokeLinecap="round"
          fill="none"
        />
        <circle cx="33.33" cy="33.33" r="6.5" fill="#4B4FE0" />
        <circle cx="66.67" cy="66.67" r="6.5" fill="#4B4FE0" />
      </svg>
      <span className="font-display text-lg font-semibold tracking-tight text-ink">
        Splenza
      </span>
    </div>
  );
}
