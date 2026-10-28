import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { components } from '../../shared/api/schema';

// The generated schema marks every field optional; these are always present in responses.
export type AuditEvent = components['schemas']['AuditEventView'] & {
  id: string;
  action: string;
  createdAt: string;
  details: Record<string, string>;
};
type AuditSlice = { items: AuditEvent[]; nextCursor?: string };
type ChainVerification = components['schemas']['ChainVerification'] & {
  intact: boolean;
  checked: number;
};

export type AuditFilters = {
  action: '' | 'people' | 'teams' | 'sign-in';
  actorId: string;
  from: string;
  to: string;
};

export const NO_FILTERS: AuditFilters = { action: '', actorId: '', from: '', to: '' };

// Date inputs give local calendar days; the API wants instants, with `to` exclusive.
const startOfDay = (day: string) => new Date(`${day}T00:00`).toISOString();
const startOfNextDay = (day: string) => {
  const next = new Date(`${day}T00:00`);
  next.setDate(next.getDate() + 1);
  return next.toISOString();
};

export function useAuditEvents(filters: AuditFilters) {
  return useInfiniteQuery({
    queryKey: ['audit-events', filters],
    initialPageParam: '',
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ size: '50' });
      if (filters.action) params.set('action', filters.action);
      if (filters.actorId) params.set('actorId', filters.actorId);
      if (filters.from) params.set('from', startOfDay(filters.from));
      if (filters.to) params.set('to', startOfNextDay(filters.to));
      if (pageParam) params.set('cursor', pageParam);
      return apiClient<AuditSlice>(`/api/v1/audit-events?${params}`);
    },
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}

export function useChainVerification() {
  return useQuery({
    queryKey: ['audit-verification'],
    queryFn: () => apiClient<ChainVerification>('/api/v1/audit-events/verification'),
  });
}
