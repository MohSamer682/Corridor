import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';

export type Card = {
  key: string;
  summary: string;
  status: string;
  type: string | null;
  priority: string | null;
  assignee: string | null;
  storyPoints: number | null;
  url: string;
};
export type Board = {
  projectKey: string;
  siteUrl: string;
  columns: { name: string; cards: Card[] }[];
  syncedAt: string | null;
  stale: boolean;
};
export type JiraConnection = {
  siteUrl: string;
  email: string;
  projectKey: string;
  webhookPath: string;
  webhookSecret: string;
  connectedAt: string;
};
export type ConnectJira = { siteUrl: string; email: string; apiToken: string; projectKey: string };

/** The cached sprint board; null when Jira isn't connected. Only asked for while the board is open. */
export function useBoard(open: boolean) {
  return useQuery({
    queryKey: ['board'],
    queryFn: () => apiClient<Board | null>('/api/v1/board').then((b) => b ?? null),
    enabled: open,
    refetchInterval: open ? 60_000 : false,
  });
}

export function useJiraConnection() {
  return useQuery({
    queryKey: ['jira-connection'],
    queryFn: () =>
      apiClient<JiraConnection | null>('/api/v1/integrations/jira').then((c) => c ?? null),
  });
}

export function useConnectJira() {
  const queryClient = useQueryClient();
  return useMutation<JiraConnection, ProblemError, ConnectJira>({
    mutationFn: (request) =>
      apiClient<JiraConnection>('/api/v1/integrations/jira', {
        method: 'PUT',
        body: JSON.stringify(request),
      }),
    onSuccess: (connection) => {
      queryClient.setQueryData(['jira-connection'], connection);
      void queryClient.invalidateQueries({ queryKey: ['board'] });
    },
  });
}
