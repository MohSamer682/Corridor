import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { apiClient } from '../../shared/api/apiClient';
import { useSessionStore } from './sessionStore';

export function useLogout() {
  const signOut = useSessionStore((state) => state.signOut);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: () => apiClient('/api/v1/auth/logout', { method: 'POST' }),
    // Sign out locally even if the server call fails; the cookie expires on its own.
    onSettled: () => {
      signOut();
      queryClient.clear();
      navigate('/login', { replace: true });
    },
  });
}
