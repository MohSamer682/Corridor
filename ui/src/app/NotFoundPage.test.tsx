import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { NotFoundPage } from './NotFoundPage';

it('tells people the page does not exist and links home', () => {
  render(
    <MemoryRouter>
      <NotFoundPage />
    </MemoryRouter>,
  );
  expect(screen.getByRole('heading', { name: 'This page doesn’t exist' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Go to the office' })).toHaveAttribute('href', '/');
});
