import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { forwardRef } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';
import { apiClient } from '../../shared/api/apiClient';
import type { ProblemError } from '../../shared/api/problem';
import { Alert } from '../../shared/ui/Alert';
import { BrandPanel } from './BrandPanel';

const signUpSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(2, 'Use 2 to 80 characters.')
    .max(80, 'Use 2 to 80 characters.'),
  email: z
    .string()
    .trim()
    .max(254, 'Enter your work email.')
    .pipe(z.email('Enter your work email.')),
  password: z.string().min(8, 'Use 8 to 128 characters.').max(128, 'Use 8 to 128 characters.'),
});
type SignUpValues = z.infer<typeof signUpSchema>;

const fieldClass =
  'h-[46px] w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink outline-none focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal aria-[invalid=true]:border-danger-ink';

export function SignUpPage() {
  const signUp = useMutation<unknown, ProblemError, SignUpValues>({
    mutationFn: (values) =>
      apiClient('/api/v1/auth/registrations', { method: 'POST', body: JSON.stringify(values) }),
  });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignUpValues>({ resolver: zodResolver(signUpSchema) });

  return (
    <div className="flex min-h-screen flex-wrap-reverse bg-surface text-ink">
      <BrandPanel />
      <main className="flex min-w-0 flex-[1_1_360px] items-center justify-center px-6 py-12">
        {signUp.isSuccess ? (
          <div role="status" className="flex w-full max-w-[380px] flex-col gap-5">
            <span className="flex size-[52px] items-center justify-center rounded-full bg-info-bg text-info-ink shadow-[inset_0_-3px_0_var(--signal)]">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="m5 12 5 5 9-10" />
              </svg>
            </span>
            <div className="flex flex-col gap-1.5">
              <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">
                Request sent
              </h1>
              <p className="m-0 text-muted">
                HR will review your account. You can sign in once they approve it.
              </p>
            </div>
            <Link
              to="/login"
              className="inline-flex h-11 items-center self-start rounded-[10px] border border-line px-[18px] font-semibold text-ink no-underline"
            >
              Back to sign in
            </Link>
          </div>
        ) : (
          <form
            noValidate
            className="flex w-full max-w-[380px] flex-col gap-5"
            onSubmit={handleSubmit((values) => signUp.mutate(values))}
          >
            <div className="flex flex-col gap-1.5">
              <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">
                Create an account
              </h1>
              <p className="m-0 text-muted">HR approves new accounts before you can sign in.</p>
            </div>
            {signUp.error && (
              <Alert tone="danger">
                {signUp.error.detail ?? 'Something went wrong. Try again.'}
              </Alert>
            )}
            <Field
              id="displayName"
              label="Full name"
              autoComplete="name"
              error={errors.displayName?.message}
              {...register('displayName')}
            />
            <Field
              id="email"
              label="Work email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
            <Field
              id="password"
              label="Password"
              type="password"
              autoComplete="new-password"
              hint="Use 8 to 128 characters."
              error={errors.password?.message}
              {...register('password')}
            />
            <button
              type="submit"
              disabled={signUp.isPending}
              className="h-12 cursor-pointer rounded-[10px] border-0 bg-accent text-[15px] font-semibold text-accent-ink disabled:cursor-wait disabled:opacity-75"
            >
              {signUp.isPending ? 'Sending…' : 'Create account'}
            </button>
            <p className="m-0 text-sm text-muted">
              Already have an account?{' '}
              <Link to="/login" className="font-semibold text-accent-text underline-offset-[3px]">
                Sign in
              </Link>
            </p>
          </form>
        )}
      </main>
    </div>
  );
}

type FieldProps = React.InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  error?: string;
  hint?: string;
};

const Field = forwardRef<HTMLInputElement, FieldProps>(function Field(
  { id, label, error, hint, ...input },
  ref,
) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13px] font-semibold">
        {label}
      </label>
      <input
        id={id}
        ref={ref}
        className={fieldClass}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...input}
      />
      {error ? (
        <p id={`${id}-error`} className="m-0 text-[13px] text-danger-ink">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="m-0 text-[13px] text-muted">
            {hint}
          </p>
        )
      )}
    </div>
  );
});
