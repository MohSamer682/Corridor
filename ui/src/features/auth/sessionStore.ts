import { create } from 'zustand';
import type { components } from '../../shared/api/schema';

export type UserView = components['schemas']['UserView'];
export type TokenResponse = components['schemas']['TokenResponse'];

type SessionState = {
  accessToken: string | null;
  user: UserView | null;
  sessionEnded: boolean;
  signIn: (token: TokenResponse) => void;
  signOut: (sessionEnded?: boolean) => void;
};

// Memory only: the access token never touches storage, and a reload restores it from the refresh cookie.
export const useSessionStore = create<SessionState>()((set) => ({
  accessToken: null,
  user: null,
  sessionEnded: false,
  signIn: (token) => set({ accessToken: token.accessToken, user: token.user, sessionEnded: false }),
  signOut: (sessionEnded = false) => set({ accessToken: null, user: null, sessionEnded }),
}));
