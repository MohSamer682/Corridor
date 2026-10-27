export function BrandMark() {
  return (
    <span className="flex items-center gap-2.5">
      <span className="flex size-8 items-center justify-center rounded-lg bg-[#23395d] shadow-[inset_0_-3px_0_var(--signal)]">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="#fff"
          strokeWidth="2.4"
          strokeLinecap="round"
          aria-hidden="true"
        >
          <path d="M4 7h16M4 12h10M4 17h16" />
        </svg>
      </span>
      <span className="font-display text-[19px] font-bold tracking-tight">Corridor</span>
    </span>
  );
}
