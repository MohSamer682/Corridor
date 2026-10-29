import { screen } from '@testing-library/react';
import { useSessionStore } from '../auth/sessionStore';
import { stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const loaded = vi.hoisted(() => ({ scene: false }));
vi.mock('./webgl', () => ({ hasWebGL: () => true }));
vi.mock('./OfficeScene', () => {
  loaded.scene = true;
  return { OfficeScene: () => <div data-testid="scene" /> };
});

beforeEach(() => {
  resetSession();
  useSessionStore.getState().signIn(tokenResponse('t'));
});

it('starts loading the 3D view while the office data is still on its way', async () => {
  stubApi(() => [200, { buildings: [] }]);
  renderApp('/office');

  expect(await screen.findByText("Your office isn't set up yet.")).toBeInTheDocument();
  expect(loaded.scene).toBe(true);
});
