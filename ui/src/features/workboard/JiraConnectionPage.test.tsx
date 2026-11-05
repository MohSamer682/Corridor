import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();
const connected = {
  siteUrl: 'https://corridor.atlassian.net',
  email: 'admin@corridor.local',
  projectKey: 'COR',
  webhookPath: '/api/v1/integrations/jira/webhook/org-1',
  webhookSecret: 'shared-secret-123',
  connectedAt: '2026-11-05T08:00:00Z',
};

function signInAs(role: 'ADMIN' | 'HR') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
}

beforeEach(() => resetSession());

it('connects Jira and shows the webhook to paste into Jira', async () => {
  signInAs('ADMIN');
  let current: unknown = null;
  stubApi((method, url) => {
    if (method === 'PUT' && url === '/api/v1/integrations/jira') {
      current = connected;
      return [200, connected];
    }
    return [200, url === '/api/v1/integrations/jira' ? current : {}];
  });
  renderApp('/admin/jira');

  expect(await screen.findByText('Not connected yet.')).toBeInTheDocument();
  await user.type(screen.getByLabelText('Site address'), 'https://corridor.atlassian.net');
  await user.type(screen.getByLabelText('Account email'), 'admin@corridor.local');
  await user.type(screen.getByLabelText('API token'), 'secret-token');
  await user.type(screen.getByLabelText('Project key'), 'cor');
  await user.click(screen.getByRole('button', { name: 'Connect' }));

  expect(
    await screen.findByText(
      (_, el) =>
        el?.tagName === 'P' &&
        /^Connected to COR on corridor\.atlassian\.net/.test(el.textContent ?? ''),
    ),
  ).toBeInTheDocument();
  expect(screen.getByLabelText('Webhook URL')).toHaveValue(
    `${location.origin}/api/v1/integrations/jira/webhook/org-1`,
  );
  expect(screen.getByLabelText('Secret')).toHaveValue('shared-secret-123');
  expect(screen.getByLabelText('API token')).toHaveValue('');
  const put = fetchCalls().find((c) => c.init.method === 'PUT');
  expect(JSON.parse(String(put?.init.body))).toEqual({
    siteUrl: 'https://corridor.atlassian.net',
    email: 'admin@corridor.local',
    apiToken: 'secret-token',
    projectKey: 'COR',
  });
});

it('shows why Jira refused the connection', async () => {
  signInAs('ADMIN');
  stubApi((method, url) => {
    if (method === 'PUT')
      return [
        422,
        {
          status: 422,
          detail:
            "Jira didn't accept the connection (Jira answered 401). Check the email, API token and project key.",
        },
      ];
    return [200, url === '/api/v1/integrations/jira' ? null : {}];
  });
  renderApp('/admin/jira');

  await user.type(await screen.findByLabelText('Site address'), 'https://corridor.atlassian.net');
  await user.type(screen.getByLabelText('Account email'), 'admin@corridor.local');
  await user.type(screen.getByLabelText('API token'), 'wrong');
  await user.type(screen.getByLabelText('Project key'), 'COR');
  await user.click(screen.getByRole('button', { name: 'Connect' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Jira answered 401');
});

it('keeps the Jira page to admins', async () => {
  signInAs('HR');
  stubApi(() => [200, { items: [] }]);
  renderApp('/admin/teams');

  await screen.findByRole('navigation', { name: 'Main' });
  expect(screen.queryByRole('link', { name: 'Jira' })).not.toBeInTheDocument();
});
