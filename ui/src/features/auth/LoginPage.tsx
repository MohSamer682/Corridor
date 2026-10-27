import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useLocation, useNavigate } from 'react-router';
import { Alert } from '../../shared/ui/Alert';
import { BrandPanel } from './BrandPanel';
import { loginSchema, type LoginValues } from './loginSchema';
import { useSessionStore } from './sessionStore';
import { useLogin } from './useLogin';

const fieldClass =
  'h-[46px] w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink outline-none focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal aria-[invalid=true]:border-danger-ink';

export function LoginPage() {
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';
  const sessionEnded = useSessionStore((state) => state.sessionEnded);
  const [showPassword, setShowPassword] = useState(false);
  const login = useLogin();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const submit = handleSubmit((values) =>
    login.mutate(values, { onSuccess: () => navigate(from, { replace: true }) }),
  );

  return (
    <div className="flex min-h-screen flex-wrap-reverse bg-surface text-ink">
      <BrandPanel />
      <main className="flex min-w-0 flex-[1_1_360px] items-center justify-center px-6 py-12">
        <form
          className="flex w-full max-w-[380px] flex-col gap-[22px]"
          noValidate
          onSubmit={submit}
        >
          <div className="flex flex-col gap-1.5">
            <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">Sign in</h1>
            <p className="m-0 text-muted">Use your work email and password.</p>
          </div>
          {sessionEnded && !login.error && (
            <Alert tone="info">Your session ended. Sign in again to continue.</Alert>
          )}
          {login.error && (
            <Alert tone="danger">{login.error.detail ?? 'Something went wrong. Try again.'}</Alert>
          )}
          <div className="flex flex-col gap-1.5">
            <label htmlFor="email" className="text-[13px] font-semibold">
              Work email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              placeholder="name@company.com"
              aria-invalid={errors.email ? true : undefined}
              aria-describedby={errors.email ? 'email-error' : undefined}
              className={fieldClass}
              {...register('email')}
            />
            {errors.email && (
              <p id="email-error" className="m-0 text-[13px] text-danger-ink">
                {errors.email.message}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="password" className="text-[13px] font-semibold">
              Password
            </label>
            <div className="relative flex">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                aria-invalid={errors.password ? true : undefined}
                aria-describedby={errors.password ? 'password-error' : undefined}
                className={`${fieldClass} pr-16`}
                {...register('password')}
              />
              <button
                type="button"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                onClick={() => setShowPassword((shown) => !shown)}
                className="absolute top-1 right-1 h-[38px] min-w-[52px] cursor-pointer rounded-lg border-0 bg-transparent text-[13px] font-semibold text-accent-text"
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            {errors.password && (
              <p id="password-error" className="m-0 text-[13px] text-danger-ink">
                {errors.password.message}
              </p>
            )}
          </div>
          <button
            type="submit"
            disabled={login.isPending}
            className="flex h-12 cursor-pointer items-center justify-center gap-2.5 rounded-[10px] border-0 bg-accent text-[15px] font-semibold text-accent-ink disabled:cursor-wait disabled:opacity-75"
          >
            {login.isPending && (
              <svg
                className="animate-spin motion-reduce:animate-none"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.4"
                strokeLinecap="round"
                aria-hidden="true"
              >
                <path d="M21 12a9 9 0 1 1-6.2-8.56" />
              </svg>
            )}
            {login.isPending ? 'Signing in…' : 'Sign in'}
          </button>
          <p className="m-0 text-[13px] text-muted">
            Trouble signing in? Ask your admin to check your account.
          </p>
        </form>
      </main>
    </div>
  );
}
