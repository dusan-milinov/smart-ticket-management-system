import { Routes } from '@angular/router';
import { authGuard, roleMatch } from './core/auth.guard';
import { ROLES } from './core/roles';
import { LoginComponent } from './features/login/login.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';

export const routes: Routes = [
    {
        path: 'login',
        component: LoginComponent
    },
    {
        path: '',
        component: MainLayoutComponent,
        canActivate: [authGuard],
        children: [
            {
                path: 'tickets/new',
                loadComponent: () => import('./features/tickets/create-ticket/create-ticket.component')
                    .then(m => m.CreateTicketComponent)
            },
            {
                path: 'users',
                canMatch: [roleMatch(ROLES.ADMIN)],
                loadComponent: () => import('./features/users/user-directory.component')
                    .then(m => m.UserDirectoryComponent)
            },
            {
                path: 'users/:id',
                canMatch: [roleMatch(ROLES.ADMIN)],
                loadComponent: () => import('./features/users/user-details.component')
                    .then(m => m.UserDetailsComponent)
            },
            {
                path: 'users',
                canMatch: [roleMatch(ROLES.SUPPORT_AGENT)],
                loadComponent: () => import('./features/users/user-directory.component')
                    .then(m => m.UserDirectoryComponent)
            },
            {
                path: 'users/:id',
                canMatch: [roleMatch(ROLES.SUPPORT_AGENT)],
                loadComponent: () => import('./features/users/user-details.component')
                    .then(m => m.UserDetailsComponent)
            },
            {
                path: 'dashboard',
                canMatch: [roleMatch(ROLES.ADMIN)],
                loadComponent: () => import('./features/dashboard/admin-dashboard/admin-dashboard.component')
                    .then(m => m.AdminDashboardComponent)
            },
            {
                path: 'dashboard',
                canMatch: [roleMatch(ROLES.SUPPORT_AGENT)],
                loadComponent: () => import('./features/dashboard/support-agent-dashboard/support-agent-dashboard.component')
                    .then(m => m.SupportAgentDashboardComponent)
            },
            {
                path: 'dashboard',
                canMatch: [roleMatch(ROLES.USER)],
                loadComponent: () => import('./features/dashboard/user-dashboard/user-dashboard.component')
                    .then(m => m.UserDashboardComponent)
            },
            {
                path: 'tickets/assigned',
                canMatch: [roleMatch(ROLES.SUPPORT_AGENT)],
                loadComponent: () => import('./features/tickets/assigned-tickets/assigned-tickets.component')
                    .then(m => m.AssignedTicketsComponent)
            },
            {
                path: 'tickets',
                canMatch: [roleMatch(ROLES.USER)],
                loadComponent: () => import('./features/tickets/my-tickets/my-tickets.component')
                    .then(m => m.MyTicketsComponent)
            },
            {
                path: '',
                pathMatch: 'full',
                redirectTo: 'dashboard'
            }
        ]
    },
    {
        path: '**',
        redirectTo: 'dashboard'
    }
];
