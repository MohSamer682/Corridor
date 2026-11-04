import { create } from 'zustand';

/** The server's view of your conversation; `groupId` is null when nobody is close enough. */
export type GroupMessage = {
  groupId: string | null;
  members: string[];
  route: 'PEER_TO_PEER' | 'SFU' | null;
};
export type ChatMessage = { id: string; senderId: string; body: string; createdAt: string };
export type Signal = { from: string; kind: 'offer' | 'answer' | 'candidate'; payload: string };

type SignalListener = (signal: Signal) => void;

type ConversationState = {
  group: GroupMessage | null;
  /** People on this floor in focus mode, yourself included. */
  focused: string[];
  messages: ChatMessage[];
  setGroup: (group: GroupMessage) => void;
  setFocused: (focused: string[]) => void;
  addMessage: (message: ChatMessage) => void;
  reset: () => void;
};

export const useConversationStore = create<ConversationState>((set) => ({
  group: null,
  focused: [],
  messages: [],
  // A new group starts a new chat; what was said before stays in history on the server.
  setGroup: (group) =>
    set((state) =>
      state.group?.groupId === group.groupId
        ? { group }
        : { group: group.groupId ? group : null, messages: [] },
    ),
  setFocused: (focused) => set({ focused }),
  addMessage: (message) =>
    set((state) =>
      state.messages.some((m) => m.id === message.id)
        ? state
        : { messages: [...state.messages, message] },
    ),
  reset: () => set({ group: null, focused: [], messages: [] }),
}));

// Signals go straight to whoever handles calls; they never need to re-render anything.
const listeners = new Set<SignalListener>();

export function onSignal(listener: SignalListener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function emitSignal(signal: Signal) {
  listeners.forEach((listener) => listener(signal));
}
