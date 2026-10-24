import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { isRole, Role } from './roles';

// Only users with a known backend role may enter the authenticated layout.
export const authGuard: CanActivateFn = () => {
    const role = inject(AuthService).user()?.role;
    return isRole(role) ? true : inject(Router).createUrlTree(['/login']);
};

// Lets several routes share one path, e.g. a different dashboard per role.
export const roleMatch = (role: Role): CanMatchFn => () =>
    inject(AuthService).user()?.role === role;
