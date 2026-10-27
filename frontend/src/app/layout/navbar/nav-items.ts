import { Role, ROLES } from '../../core/roles';

export interface NavItem {
  label: string;
  path: string;
}

const PROFILE_ITEM: NavItem = { label: 'Profile', path: '/profile' };

export const NAV_ITEMS: Record<Role, NavItem[]> = {
  [ROLES.USER]: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'My Tickets', path: '/tickets' },
    { label: 'Create Ticket', path: '/tickets/new' },
    PROFILE_ITEM,
  ],
  [ROLES.SUPPORT_AGENT]: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Assigned Tickets', path: '/tickets/assigned' },
    { label: 'Workload', path: '/workload' },
    PROFILE_ITEM,
  ],
  [ROLES.ADMIN]: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Users', path: '/users' },
    { label: 'New Support Agent', path: '/users/new-agent' },
    { label: 'Categories', path: '/categories' },
    { label: 'Analytics', path: '/analytics' },
    PROFILE_ITEM,
  ],
};
