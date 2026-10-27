import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <div className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="flex w-full max-w-[460px] flex-col items-center gap-4 text-center">
        <h1 className="m-0 font-display text-3xl font-bold tracking-tight">
          This page doesn’t exist
        </h1>
        <p className="m-0 text-muted">The link may be old, or the room was moved.</p>
        <Link
          to="/"
          className="inline-flex h-[46px] items-center rounded-[10px] bg-accent px-5 font-semibold text-accent-ink no-underline"
        >
          Go to the office
        </Link>
      </div>
    </div>
  );
}
