import { useState, type FormEvent } from 'react';
import { Alert } from '../../shared/ui/Alert';
import { useConnectJira, useJiraConnection, type JiraConnection } from './useWorkboard';

const field =
  'h-11 w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';
const label = 'mb-1.5 block text-[13px] font-semibold';
const primary =
  'h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-70';
const empty = { siteUrl: '', email: '', apiToken: '', projectKey: '' };

/** Admins connect the company's Jira project; every floor's sprint board then shows it. */
export function JiraConnectionPage() {
  const connection = useJiraConnection();
  return (
    <div className="mx-auto box-border flex w-full max-w-[760px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div>
        <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">Jira</h1>
        <p className="m-0 mt-1 text-muted">
          The sprint board on every floor shows this project. Corridor only reads from Jira.
        </p>
      </div>
      <section className="rounded-[14px] border border-line bg-surface p-5 sm:p-7">
        {connection.isPending && <p className="m-0 text-muted">Loading…</p>}
        {connection.isError && (
          <p role="alert" className="m-0 font-semibold text-danger-ink">
            We couldn't load the Jira connection.
          </p>
        )}
        {connection.isSuccess &&
          (connection.data ? (
            <Connected connection={connection.data} />
          ) : (
            <p className="m-0 text-muted">Not connected yet.</p>
          ))}
      </section>
      <ConnectForm />
    </div>
  );
}

function Connected({ connection }: { connection: JiraConnection }) {
  const host = new URL(connection.siteUrl).host;
  return (
    <div className="flex flex-col gap-4">
      <p className="m-0">
        Connected to <b>{connection.projectKey}</b> on {host} as {connection.email}.
      </p>
      <p className="m-0 text-sm text-muted">
        For instant updates, add a webhook in Jira (Settings, System, WebHooks) for issue created,
        updated and deleted, with this URL and secret. Without it the board refreshes every 5
        minutes.
      </p>
      <div>
        <label className={label} htmlFor="jira-webhook">
          Webhook URL
        </label>
        <input
          id="jira-webhook"
          readOnly
          className={`${field} font-mono text-sm`}
          value={`${location.origin}${connection.webhookPath}`}
          onFocus={(e) => e.target.select()}
        />
      </div>
      <div>
        <label className={label} htmlFor="jira-secret">
          Secret
        </label>
        <input
          id="jira-secret"
          readOnly
          className={`${field} font-mono text-sm`}
          value={connection.webhookSecret}
          onFocus={(e) => e.target.select()}
        />
      </div>
    </div>
  );
}

function ConnectForm() {
  const connect = useConnectJira();
  const [form, setForm] = useState(empty);
  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: e.target.value }));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    connect.mutate(
      { ...form, projectKey: form.projectKey.trim().toUpperCase() },
      { onSuccess: () => setForm((current) => ({ ...current, apiToken: '' })) },
    );
  };

  return (
    <form
      onSubmit={submit}
      className="flex flex-col gap-[18px] rounded-[14px] border border-line bg-surface p-5 sm:p-7"
    >
      <h2 className="m-0 font-display text-lg font-bold tracking-[-0.01em]">
        Connect with an API token
      </h2>
      {connect.error && (
        <Alert tone="danger">{connect.error.detail ?? 'That didn’t work. Try again.'}</Alert>
      )}
      <div>
        <label className={label} htmlFor="jira-site">
          Site address
        </label>
        <input
          id="jira-site"
          className={field}
          type="url"
          required
          placeholder="https://yourteam.atlassian.net"
          value={form.siteUrl}
          onChange={set('siteUrl')}
        />
      </div>
      <div className="grid gap-[18px] sm:grid-cols-2">
        <div>
          <label className={label} htmlFor="jira-email">
            Account email
          </label>
          <input
            id="jira-email"
            className={field}
            type="email"
            required
            autoComplete="off"
            value={form.email}
            onChange={set('email')}
          />
        </div>
        <div>
          <label className={label} htmlFor="jira-key">
            Project key
          </label>
          <input
            id="jira-key"
            className={`${field} uppercase`}
            required
            placeholder="COR"
            value={form.projectKey}
            onChange={set('projectKey')}
          />
        </div>
      </div>
      <div>
        <label className={label} htmlFor="jira-token">
          API token
        </label>
        <input
          id="jira-token"
          className={field}
          type="password"
          required
          autoComplete="new-password"
          aria-describedby="jira-token-hint"
          value={form.apiToken}
          onChange={set('apiToken')}
        />
        <p id="jira-token-hint" className="m-0 mt-1.5 text-[13px] text-muted">
          Create one at id.atlassian.com, Security, API tokens. It's stored encrypted and never
          shown again.
        </p>
      </div>
      <div className="flex justify-end">
        <button type="submit" className={primary} disabled={connect.isPending}>
          {connect.isPending ? 'Connecting…' : 'Connect'}
        </button>
      </div>
    </form>
  );
}
