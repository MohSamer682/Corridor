import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { AppShell } from './AppShell';

it('gives keyboard users a skip link to the main content', () => {
  render(
    <MemoryRouter>
      <AppShell />
    </MemoryRouter>,
  );
  expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main');
  expect(screen.getByRole('main')).toHaveAttribute('id', 'main');
  expect(screen.getByRole('banner')).toHaveTextContent('Corridor');
});
