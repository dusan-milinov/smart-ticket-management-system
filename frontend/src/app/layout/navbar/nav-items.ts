import { Role, ROLES } from '../../core/roles';

export interface NavItem {
  label: string;
  path: string;
}

export const NAV_ITEMS: Record<Role, NavItem[]> = {
  [ROLES.USER]: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'My Tickets', path: '/tickets' },
    { label: 'Create Ticket', path: '/tickets/new' },
  ],
  [ROLES.SUPPORT_AGENT]: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Assigned Tickets', path: '/tickets/assigned' },
    { label: 'Workload', path: '/workload' },
  ],
  [ROLES.ADMIN]: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Users', path: '/users' },
    { label: 'Categories', path: '/categories' },
    { label: 'Analytics', path: '/analytics' },
  ],
};
