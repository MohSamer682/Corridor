import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';

export type PunchView = {
  punchedIn: boolean;
  since: string | null;
  lastOut: string | null;
  workedTodayMinutes: number;
};
export type Span = { from: string; to: string | null };
export type WorkDay = {
  day: string;
  status: 'PRESENT' | 'ABSENT' | 'WEEKEND' | 'NOT_IN_YET';
  intervals: Span[];
  workedMinutes: number;
  lateMinutes: number;
  inNow: boolean;
};
export type TeamDay = {
  userId: string;
  displayName: string;
  teamName: string | null;
  day: WorkDay;
};
type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

const iso = (date: Date) => date.toLocaleDateString('en-CA');

export function useMyPunch() {
  return useQuery({
    queryKey: ['punch'],
    queryFn: () => apiClient<PunchView>('/api/v1/me/punch'),
    refetchInterval: 60_000,
  });
}

/** Punch in or out. One key per press, so a double click or a retry never punches twice. */
export function usePunch() {
  const queryClient = useQueryClient();
  return useMutation<PunchView, ProblemError, 'in' | 'out'>({
    mutationFn: (direction) =>
      apiClient<PunchView>(`/api/v1/me/punch-${direction}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      }),
    onSuccess: (view) => {
      queryClient.setQueryData(['punch'], view);
      return queryClient.invalidateQueries({ queryKey: ['attendance'] });
    },
  });
}

/** The last seven days, newest first. */
export function useMyWeek() {
  const [[from, to]] = useState(() => {
    const end = new Date();
    const start = new Date(end);
    start.setDate(end.getDate() - 6);
    return [start, end] as const;
  });
  return useQuery({
    queryKey: ['attendance', 'me', iso(from), iso(to)],
    queryFn: () => apiClient<WorkDay[]>(`/api/v1/me/attendance?from=${iso(from)}&to=${iso(to)}`),
  });
}

export function useTeamDay(day: string, teamId: string, query: string, page: number) {
  const params = new URLSearchParams({ day, page: String(page) });
  if (teamId) params.set('teamId', teamId);
  if (query.trim()) params.set('query', query.trim());
  return useQuery<Page<TeamDay>, ProblemError>({
    queryKey: ['attendance', 'team', day, teamId, query, page],
    queryFn: () => apiClient<Page<TeamDay>>(`/api/v1/attendance?${params}`),
    placeholderData: keepPreviousData,
  });
}

export const today = () => iso(new Date());
