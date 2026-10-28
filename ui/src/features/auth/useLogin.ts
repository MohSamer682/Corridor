import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';
import type { LoginValues } from './loginSchema';
import { useSessionStore, type TokenResponse } from './sessionStore';

export function useLogin() {
  const signIn = useSessionStore((state) => state.signIn);
  return useMutation<TokenResponse, ProblemError, LoginValues>({
    mutationFn: (values) =>
      apiClient<TokenResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify(values),
      }),
    onSuccess: signIn,
  });
}
