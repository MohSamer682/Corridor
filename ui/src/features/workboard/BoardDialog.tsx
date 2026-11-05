import * as Dialog from '@radix-ui/react-dialog';
import { useBoard, type Board, type Card } from './useWorkboard';

const minutesAgo = (iso: string) => {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return 'less than a minute ago';
  if (minutes < 60) return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} ago`;
  const hours = Math.round(minutes / 60);
  return `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
};
const count = (n: number) => (n === 0 ? 'no issues' : `${n} ${n === 1 ? 'issue' : 'issues'}`);

/** The sprint board on the floor, read-only, from the copy the server keeps of Jira. */
export function BoardDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const board = useBoard(open);
  const title = board.data ? `${board.data.projectKey} sprint board` : 'Sprint board';
  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-[rgba(12,17,27,0.45)]" />
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed inset-x-3 top-[5vh] mx-auto flex max-h-[90vh] max-w-[1120px] flex-col rounded-[14px] border border-line bg-surface text-ink shadow-[0_24px_64px_rgba(12,17,27,0.28)] outline-none"
        >
          <div className="flex items-start justify-between gap-3 border-b border-line px-5 pt-5 pb-4 sm:px-7">
            <div className="min-w-0">
              <Dialog.Title className="m-0 font-display text-[22px] font-bold tracking-[-0.02em]">
                {title}
              </Dialog.Title>
              {board.data && (
                <p className="m-0 mt-0.5 truncate text-sm text-muted">
                  Read-only, from {new URL(board.data.siteUrl).host}
                </p>
              )}
            </div>
            <Dialog.Close className="h-11 cursor-pointer rounded-[10px] border-0 bg-transparent px-2.5 font-semibold text-accent-text">
              Close
            </Dialog.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-auto px-5 py-5 sm:px-7">
            <BoardBody board={board.data} pending={board.isPending} failed={board.isError} />
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function BoardBody({
  board,
  pending,
  failed,
}: {
  board: Board | null | undefined;
  pending: boolean;
  failed: boolean;
}) {
  if (pending) return <p className="m-0 text-muted">Loading the board…</p>;
  if (failed)
    return (
      <p role="alert" className="m-0 font-semibold text-danger-ink">
        We couldn't load the board. Try again in a moment.
      </p>
    );
  if (!board)
    return (
      <p className="m-0 text-muted">
        Jira isn't connected yet. An admin can connect it on the Jira page.
      </p>
    );
  return (
    <div className="flex flex-col gap-4">
      {board.stale && (
        <p
          role="status"
          className="m-0 rounded-[10px] bg-warn-bg px-3.5 py-3 text-sm text-warn-ink"
        >
          {board.syncedAt
            ? `Jira is unreachable. Showing the board from ${minutesAgo(board.syncedAt)}.`
            : 'Jira is unreachable, and the board hasn’t loaded yet.'}
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {board.columns.map((column) => (
          <section
            key={column.name}
            aria-label={`${column.name}, ${count(column.cards.length)}`}
            className="flex min-w-0 flex-col gap-2 rounded-[12px] bg-field p-2.5"
          >
            <h3 className="m-0 flex items-center justify-between px-1 pt-0.5 text-[13px] font-semibold tracking-[0.04em] text-muted uppercase">
              {column.name}
              <span className="tabular-nums">{column.cards.length}</span>
            </h3>
            {column.cards.map((card) => (
              <IssueCard key={card.key} card={card} />
            ))}
          </section>
        ))}
      </div>
    </div>
  );
}

function IssueCard({ card }: { card: Card }) {
  const meta = [
    card.assignee ?? 'Unassigned',
    card.storyPoints != null ? `${card.storyPoints} pts` : null,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <a
      href={card.url}
      target="_blank"
      rel="noreferrer"
      className="flex flex-col gap-1 rounded-[10px] border border-line bg-surface p-3 text-ink no-underline hover:border-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal"
    >
      <span className="flex items-center justify-between gap-2 text-[12px] font-semibold text-muted">
        <span className="font-mono">{card.key}</span>
        {card.type && <span>{card.type}</span>}
      </span>
      <span className="text-sm leading-snug">{card.summary}</span>
      <span className="text-[12px] text-muted">{meta}</span>
    </a>
  );
}
