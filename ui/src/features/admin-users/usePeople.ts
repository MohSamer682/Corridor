import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';
import type { components } from '../../shared/api/schema';

export type UserSummary = components['schemas']['UserSummary'];
export type UserView = components['schemas']['UserView'];
export type TeamView = components['schemas']['TeamView'];
type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

export type PeopleFilters = {
  status: '' | 'ACTIVE' | 'SUSPENDED';
  teamId: string;
  query: string;
  page: number;
};
export const PAGE_SIZE = 20;

export function usePeople(filters: PeopleFilters) {
  const params = new URLSearchParams({ page: String(filters.page), size: String(PAGE_SIZE) });
  if (filters.status) params.set('status', filters.status);
  if (filters.teamId) params.set('teamId', filters.teamId);
  if (filters.query.trim()) params.set('query', filters.query.trim());
  return useQuery({
    queryKey: ['users', filters],
    queryFn: () => apiClient<Page<UserSummary>>(`/api/v1/users?${params}`),
    placeholderData: keepPreviousData,
  });
}

export function usePerson(id: string | null) {
  return useQuery({
    queryKey: ['user', id],
    queryFn: () => apiClient<UserView>(`/api/v1/users/${id}`),
    enabled: id !== null,
  });
}

export function useTeams() {
  return useQuery({
    queryKey: ['teams'],
    queryFn: () => apiClient<Page<TeamView>>('/api/v1/teams?size=100'),
  });
}

export function useAdminMutation<TVariables>(request: (variables: TVariables) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ProblemError, TVariables>({
    mutationFn: request,
    onSuccess: () =>
      Promise.all(
        ['users', 'user', 'teams'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
      ),
  });
}

export function ifMatch(version: number | undefined) {
  return { 'If-Match': `"${version ?? 0}"` };
}
