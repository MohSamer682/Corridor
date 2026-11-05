import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';

export type Announcement = {
  id: string;
  body: string;
  teamName: string | null;
  authorId: string;
  authorName: string;
  createdAt: string;
};
export type TownHall = {
  title: string;
  floorId: string;
  x: number;
  y: number;
  roomName: string;
  calledBy: string;
  startedAt: string;
};
export type Sent = { id: string; audience: number; online: number };

/** Unread announcements; the floor's socket also refreshes this the moment one arrives. */
export function useUnread() {
  return useQuery({
    queryKey: ['announcements'],
    queryFn: () => apiClient<Announcement[]>('/api/v1/announcements/unread'),
    refetchInterval: 20_000,
  });
}

export function useTownHall() {
  return useQuery({
    queryKey: ['town-hall'],
    // 204 when none is live comes back as an empty body.
    queryFn: () => apiClient<TownHall | null>('/api/v1/town-hall').then((t) => t ?? null),
    refetchInterval: 20_000,
  });
}

export function useDismiss() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ProblemError, string>({
    mutationFn: (id) => apiClient(`/api/v1/announcements/${id}/read`, { method: 'POST' }),
    onMutate: (id) =>
      queryClient.setQueryData<Announcement[]>(['announcements'], (list) =>
        list?.filter((a) => a.id !== id),
      ),
  });
}

export function useBroadcast() {
  return useMutation<Sent, ProblemError, { teamId: string | null; body: string }>({
    mutationFn: (broadcast) =>
      apiClient<Sent>('/api/v1/announcements', { method: 'POST', body: JSON.stringify(broadcast) }),
  });
}

export function useCallTownHall() {
  const queryClient = useQueryClient();
  return useMutation<TownHall | null, ProblemError, { title: string } | null>({
    mutationFn: (call) =>
      call
        ? apiClient<TownHall>('/api/v1/town-hall', { method: 'POST', body: JSON.stringify(call) })
        : apiClient('/api/v1/town-hall', { method: 'DELETE' }).then(() => null),
    onSuccess: (live) => queryClient.setQueryData(['town-hall'], live),
  });
}
