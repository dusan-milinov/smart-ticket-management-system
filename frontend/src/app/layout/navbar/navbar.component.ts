import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { isRole, ROLE_LABELS } from '../../core/roles';
import { NAV_ITEMS } from './nav-items';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  styleUrl: './navbar.component.css',
  templateUrl: './navbar.component.html',
})
export class NavbarComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.authService.user;
  protected readonly menuOpen = signal(false);

  protected readonly navItems = computed(() => {
    const role = this.user()?.role;
    return isRole(role) ? NAV_ITEMS[role] : [];
  });

  protected readonly roleLabel = computed(() => {
    const role = this.user()?.role;
    return isRole(role) ? ROLE_LABELS[role] : '';
  });

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    const goToLogin = () => this.router.navigate(['/login']);
    this.closeMenu();
    this.authService.logout().subscribe({ next: goToLogin, error: goToLogin });
  }
}
