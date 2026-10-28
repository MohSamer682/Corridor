import * as Dialog from '@radix-ui/react-dialog';
import { useRef, type ReactNode } from 'react';

type SheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
};

export function Sheet({ open, onOpenChange, title, description, children }: SheetProps) {
  // Radix only returns focus to a Dialog.Trigger; these sheets open from table rows instead.
  const opener = useRef<HTMLElement | null>(null);
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-[rgba(12,17,27,0.45)]" />
        <Dialog.Content
          aria-describedby={undefined}
          onOpenAutoFocus={() => {
            opener.current =
              document.activeElement instanceof HTMLElement ? document.activeElement : null;
          }}
          onCloseAutoFocus={(event) => {
            if (opener.current?.isConnected) {
              event.preventDefault();
              opener.current.focus();
            }
          }}
          className="fixed inset-y-0 right-0 flex w-full max-w-[460px] flex-col border-l border-line bg-surface text-ink shadow-[-12px_0_32px_rgba(12,17,27,0.18)] outline-none"
        >
          <div className="flex items-start justify-between gap-3 border-b border-line px-7 pt-6 pb-4">
            <div>
              <Dialog.Title className="m-0 font-display text-[22px] font-bold tracking-[-0.02em]">
                {title}
              </Dialog.Title>
              {description && <p className="m-0 mt-0.5 text-muted">{description}</p>}
            </div>
            <Dialog.Close className="h-11 cursor-pointer rounded-[10px] border-0 bg-transparent px-2.5 font-semibold text-accent-text">
              Close
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
