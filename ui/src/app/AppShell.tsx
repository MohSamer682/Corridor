import { Link, Outlet } from 'react-router';
import { BrandMark } from './BrandMark';

export function AppShell() {
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only rounded-md bg-surface px-4 py-2 focus:not-sr-only focus:absolute focus:top-2 focus:left-2"
      >
        Skip to content
      </a>
      <header className="flex items-center border-b border-line bg-surface px-[max(16px,3%)] py-3">
        <Link to="/" className="flex min-h-11 items-center text-ink no-underline">
          <BrandMark />
        </Link>
      </header>
      <main id="main" className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
