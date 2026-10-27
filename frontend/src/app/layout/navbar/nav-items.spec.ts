import { NAV_ITEMS } from './nav-items';
import { ROLES } from '../../core/roles';

describe('NAV_ITEMS', () => {
  it('shows Profile to every role and limits user management links to administrators', () => {
    for (const role of Object.values(ROLES)) {
      expect(NAV_ITEMS[role].some((item) => item.path === '/profile')).toBe(true);
    }

    expect(NAV_ITEMS[ROLES.SUPPORT_AGENT].some((item) => item.path === '/users')).toBe(false);
    expect(NAV_ITEMS[ROLES.ADMIN]).toContainEqual({ label: 'New Support Agent', path: '/users/new-agent' });
  });
});
