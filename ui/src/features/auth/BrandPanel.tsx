import { BrandMark } from '../../app/BrandMark';

export function BrandPanel() {
  return (
    <section className="flex min-w-0 flex-[1_1_360px] flex-col justify-between gap-10 bg-brand-bg px-[max(24px,6%)] pt-12 pb-10 text-brand-ink">
      <BrandMark size="lg" />
      <div className="flex max-w-[460px] flex-col gap-5">
        <span className="text-xs font-semibold tracking-[0.12em] text-signal uppercase">
          Corridor Technologies · New Cairo
        </span>
        <h2 className="m-0 font-display text-[40px] leading-[1.08] font-bold tracking-[-0.03em] text-balance">
          Walk up to your team, wherever they work.
        </h2>
        <p className="m-0 max-w-[400px] text-brand-muted">
          Your company's office, online. Buildings, floors and rooms where a conversation starts by
          walking over.
        </p>
      </div>
      <svg
        viewBox="0 0 420 200"
        className="h-auto w-full max-w-[420px]"
        fill="none"
        aria-hidden="true"
      >
        <g className="stroke-brand-line" strokeOpacity="0.45" strokeWidth="1.2">
          <path d="M60 150 L210 190 L360 150 L210 110 Z" />
          <path d="M60 108 L210 148 L360 108 L210 68 Z" />
          <path d="M60 66 L210 106 L360 66 L210 26 Z" />
          <path d="M60 150 V66 M360 150 V66 M210 190 V106" />
          <path d="M135 88 L210 108 M210 68 L285 88 M120 128 L195 148 M240 140 L300 124" />
        </g>
        <g className="fill-signal">
          <circle cx="168" cy="78" r="4" />
          <circle cx="182" cy="82" r="4" />
          <circle cx="250" cy="122" r="4" />
          <circle cx="236" cy="118" r="4" fillOpacity="0.5" />
        </g>
        <circle
          cx="168"
          cy="78"
          r="16"
          className="stroke-signal"
          strokeOpacity="0.6"
          strokeWidth="1"
        />
      </svg>
      <span className="text-[13px] text-brand-faint">3 floors at HQ · 1 at the Innovation Lab</span>
    </section>
  );
}
