import { describeEvent } from './describeEvent';

function event(action: string, details: Record<string, string>) {
  return {
    id: 'e1',
    action,
    createdAt: '2026-10-28T12:32:00Z',
    targetType: 'user',
    details,
  };
}

it('names the person and the change for people events', () => {
  expect(describeEvent(event('user.suspended', { name: 'Omar Adel' }))).toEqual({
    lead: 'Suspended',
    subject: 'Omar Adel',
    tail: '',
    detail: '',
    kind: 'People',
  });
  expect(
    describeEvent(
      event('user.role_changed', { name: 'Salma Ibrahim', from: 'EMPLOYEE', to: 'MANAGER' }),
    ),
  ).toMatchObject({
    lead: 'Changed',
    subject: 'Salma Ibrahim',
    tail: "'s role",
    detail: 'Member → Manager',
  });
  expect(
    describeEvent(
      event('user.approved', { name: 'Youssef Nabil', role: 'EMPLOYEE', team: 'Product' }),
    ),
  ).toMatchObject({ lead: 'Approved', tail: ' as Member', detail: 'Joined Product' });
});

it('files team moves and team changes under Teams', () => {
  expect(
    describeEvent(event('user.team_changed', { name: 'Nour Hassan', to: 'Product' })),
  ).toMatchObject({
    lead: 'Moved',
    subject: 'Nour Hassan',
    detail: 'No team → Product',
    kind: 'Teams',
  });
  expect(
    describeEvent(
      event('team.renamed', { name: 'Platform', from: 'Core platform', to: 'Platform' }),
    ),
  ).toMatchObject({
    lead: 'Renamed team',
    subject: 'Platform',
    detail: 'Was "Core platform"',
    kind: 'Teams',
  });
});

it('explains why a sign-in failed', () => {
  expect(
    describeEvent(
      event('auth.login_failed', {
        email: 'ceo@corridor.local',
        reason: 'unknown_email',
        ip: '1.2.3.4',
      }),
    ),
  ).toEqual({
    lead: 'Failed sign-in for',
    subject: 'ceo@corridor.local',
    tail: '',
    detail: 'No account with this email',
    kind: 'Sign-in',
  });
});

it('falls back to the action name for events this screen does not know yet', () => {
  expect(describeEvent(event('attendance.punched_in', {}))).toMatchObject({
    lead: 'attendance.punched_in',
    kind: 'Other',
  });
});
