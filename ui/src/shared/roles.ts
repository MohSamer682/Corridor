import type { components } from './api/schema';

export type Role = NonNullable<components['schemas']['UserView']['role']>;

export const roleLabels: Record<Role, string> = {
  ADMIN: 'Admin',
  HR: 'HR',
  MANAGER: 'Manager',
  EMPLOYEE: 'Member',
};
