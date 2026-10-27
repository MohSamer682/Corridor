import type { ReactNode } from 'react';

const tones = {
  danger: { role: 'alert', className: 'bg-danger-bg text-danger-ink', icon: 'M12 8v5M12 16h.01' },
  info: { role: 'status', className: 'bg-info-bg text-info-ink', icon: 'M12 7v5l3 2' },
} as const;

export function Alert({ tone, children }: { tone: keyof typeof tones; children: ReactNode }) {
  const { role, className, icon } = tones[tone];
  return (
    <div
      role={role}
      className={`flex items-start gap-2.5 rounded-[10px] px-3.5 py-3 text-sm ${className}`}
    >
      <svg
        className="mt-px flex-none"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <path d={icon} />
      </svg>
      <span>{children}</span>
    </div>
  );
}
