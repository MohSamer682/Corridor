const clockFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
const dayFormat = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

export const clock = (iso: string) => clockFormat.format(new Date(iso));

/** 492 → "8h 12m"; under an hour → "25m". */
export function duration(minutes: number) {
  const hours = Math.floor(minutes / 60);
  return hours ? `${hours}h ${minutes % 60}m` : `${minutes}m`;
}

/** "2026-11-04" → "Wed 4 Nov", read as a calendar date, not a moment. */
export const dayName = (date: string) => dayFormat.format(new Date(`${date}T12:00:00`));
