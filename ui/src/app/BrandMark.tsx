const sizes = {
  md: { tile: 'size-8 rounded-lg', icon: 18, word: 'text-[19px]' },
  lg: { tile: 'size-9 rounded-[9px]', icon: 20, word: 'text-[22px]' },
};

export function BrandMark({ size = 'md' }: { size?: keyof typeof sizes }) {
  const { tile, icon, word } = sizes[size];
  return (
    <span className="flex items-center gap-2.5">
      <span
        className={`flex items-center justify-center bg-[#23395d] shadow-[inset_0_-3px_0_var(--signal)] ${tile}`}
      >
        <svg
          width={icon}
          height={icon}
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
      <span className={`font-display font-bold tracking-[-0.03em] ${word}`}>Corridor</span>
    </span>
  );
}
