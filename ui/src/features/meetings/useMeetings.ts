import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';

export type MeetingStatus = 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELLED';
export type Meeting = {
  id: string;
  title: string;
  roomId: string;
  roomName: string;
  place: string;
  floorId: string | null;
  startsAt: string;
  endsAt: string;
  status: MeetingStatus;
  organizerId: string;
  attendees: string[];
};
export type BookableRoom = { id: string; name: string; floorId: string; place: string };
export type Invitee = {
  userId: string;
  displayName: string;
  teamId: string | null;
  teamName: string | null;
};
export type ScheduleRequest = {
  roomId: string;
  title: string;
  startsAt: string;
  minutes: number;
  invitees: string[];
};
export type Join = { url: string; token: string };

/** Join opens this long before the start. */
export const EARLY_MS = 10 * 60_000;

/** Your meetings from the start of today to a week from now. */
export function useMyMeetings() {
  return useQuery({
    queryKey: ['meetings'],
    queryFn: () => {
      const from = new Date();
      from.setHours(0, 0, 0, 0);
      const to = new Date(from);
      to.setDate(from.getDate() + 7);
      return apiClient<Meeting[]>(
        `/api/v1/meetings?from=${from.toISOString()}&to=${to.toISOString()}`,
      );
    },
    refetchInterval: 30_000,
  });
}

export function useMeetingRooms() {
  return useQuery({
    queryKey: ['meeting-rooms'],
    queryFn: () => apiClient<BookableRoom[]>('/api/v1/meeting-rooms'),
    staleTime: 5 * 60_000,
  });
}

export function useInvitees() {
  return useQuery({
    queryKey: ['meeting-invitees'],
    queryFn: () => apiClient<Invitee[]>('/api/v1/meeting-invitees'),
    staleTime: 5 * 60_000,
  });
}

export function useSchedule() {
  const queryClient = useQueryClient();
  return useMutation<Meeting, ProblemError, ScheduleRequest>({
    mutationFn: (request) =>
      apiClient<Meeting>('/api/v1/meetings', {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify(request),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['meetings'] }),
  });
}

export function useCancelMeeting() {
  const queryClient = useQueryClient();
  return useMutation<unknown, ProblemError, string>({
    mutationFn: (id) => apiClient(`/api/v1/meetings/${id}/cancellation`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['meetings'] }),
  });
}

export function useJoinMeeting() {
  return useMutation<Join, ProblemError, string>({
    mutationFn: (id) => apiClient<Join>(`/api/v1/meetings/${id}/token`, { method: 'POST' }),
  });
}

/** Whether Join shows: from 10 minutes before the start until the end, unless cancelled. */
export function canJoin(meeting: Meeting, now: number) {
  return (
    (meeting.status === 'SCHEDULED' || meeting.status === 'LIVE') &&
    now >= Date.parse(meeting.startsAt) - EARLY_MS &&
    now < Date.parse(meeting.endsAt)
  );
}
