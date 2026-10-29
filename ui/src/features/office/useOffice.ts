import { useQueries, useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { Campus, FloorLayout } from './types';

export function useCampus() {
  return useQuery({
    queryKey: ['campus'],
    queryFn: () => apiClient<Campus>('/api/v1/campus'),
    staleTime: 5 * 60_000,
  });
}

// Layouts change only when a new version is published; the browser revalidates them with the ETag.
export function useFloorLayouts(floorIds: string[]) {
  return useQueries({
    queries: floorIds.map((id) => ({
      queryKey: ['floor-layout', id],
      queryFn: () => apiClient<FloorLayout>(`/api/v1/floors/${id}/layout`),
      staleTime: Infinity,
    })),
    combine: (results) => ({
      layouts: results.flatMap((result) => (result.data ? [result.data] : [])),
      isPending: results.some((result) => result.isPending),
      isError: results.some((result) => result.isError),
      refetch: () => results.forEach((result) => result.refetch()),
    }),
  });
}
