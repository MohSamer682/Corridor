import { roleLabels, type Role } from '../../shared/roles';

export type Kind = 'People' | 'Teams' | 'Sign-in' | 'Attendance' | 'Other';

/** Renders as `{lead} <b>{subject}</b>{tail}`, with `detail` on a second, quieter line. */
export type EventDescription = {
  lead: string;
  subject: string;
  tail: string;
  detail: string;
  kind: Kind;
};

type DescribableEvent = { action: string; details: Record<string, string> };

const signInReasons: Record<string, string> = {
  unknown_email: 'No account with this email',
  wrong_password: 'Wrong password',
  pending: 'Request not approved yet',
  suspended: 'Account suspended',
};

const role = (value: string | undefined) => (value ? (roleLabels[value as Role] ?? value) : '');
const change = (from: string, to: string) => `${from} → ${to}`;
const clockFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
const clock = (iso: string | undefined) => (iso ? clockFormat.format(new Date(iso)) : '');
const minutes = (value: string | undefined) => {
  const total = Number(value ?? 0);
  return `${Math.floor(total / 60)}h ${total % 60}m`;
};

export function describeEvent({ action, details }: DescribableEvent): EventDescription {
  const name = details.name ?? '';
  const people = (lead: string, tail = '', detail = '') =>
    ({ lead, subject: name, tail, detail, kind: 'People' }) as const;
  const teams = (lead: string, detail = '') =>
    ({ lead, subject: name, tail: '', detail, kind: 'Teams' }) as const;

  switch (action) {
    case 'user.created':
      return people('Added', '', role(details.role));
    case 'user.approved':
      return people(
        'Approved',
        ` as ${role(details.role)}`,
        details.team ? `Joined ${details.team}` : '',
      );
    case 'user.declined':
      return people('Declined the request from', '', details.email ?? '');
    case 'user.suspended':
      return people('Suspended');
    case 'user.reactivated':
      return people('Reactivated');
    case 'user.removed':
      return people('Removed');
    case 'user.role_changed':
      return people('Changed', "'s role", change(role(details.from), role(details.to)));
    case 'user.team_changed':
      return teams('Moved', change(details.from ?? 'No team', details.to ?? 'No team'));
    case 'team.created':
      return teams('Created team');
    case 'team.renamed':
      return teams('Renamed team', `Was "${details.from}"`);
    case 'team.recolored':
      return teams('Changed the color of team');
    case 'team.deleted':
      return teams('Deleted team');
    case 'attendance.punched_in':
      return {
        lead: 'Punched in',
        subject: '',
        tail: '',
        detail: clock(details.at),
        kind: 'Attendance',
      };
    case 'attendance.punched_out':
      return {
        lead: 'Punched out',
        subject: '',
        tail: '',
        detail: `${clock(details.from)} – ${clock(details.to)} · ${minutes(details.minutes)}`,
        kind: 'Attendance',
      };
    case 'auth.login_failed':
      return {
        lead: 'Failed sign-in for',
        subject: details.email ?? '',
        tail: '',
        detail: signInReasons[details.reason ?? ''] ?? '',
        kind: 'Sign-in',
      };
    default:
      return { lead: action, subject: '', tail: '', detail: '', kind: 'Other' };
  }
}
