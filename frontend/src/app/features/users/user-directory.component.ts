import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { ROLES } from '../../core/roles';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { AccountDirectoryEntry } from './account-directory-entry.model';
import { UserDirectoryService } from './user-directory.service';

const PAGE_SIZE = 10;

type DirectoryList = 'users' | 'agents';

@Component({
  imports: [PageHeaderComponent, PaginationComponent, RouterLink],
  selector: 'app-user-directory',
  styleUrl: './user-directory.component.css',
  templateUrl: './user-directory.component.html',
})
export class UserDirectoryComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly directoryService = inject(UserDirectoryService);

  protected readonly isAdmin = computed(() => this.authService.user()?.role === ROLES.ADMIN);
  protected readonly list = signal<DirectoryList>('users');
  protected readonly accounts = signal<AccountDirectoryEntry[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pending = signal<AccountDirectoryEntry | null>(null);
  protected readonly actionError = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  protected selectList(list: DirectoryList): void {
    if (!this.isAdmin() || this.list() === list) {
      return;
    }
    this.list.set(list);
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set(null);

    const result = this.isAdmin()
      ? this.list() === 'agents'
        ? this.directoryService.getSupportAgents(page, PAGE_SIZE)
        : this.directoryService.getAllUsers(page, PAGE_SIZE)
      : this.directoryService.getNonAdminUsers(page, PAGE_SIZE);

    result.subscribe({
      next: (response) => {
        this.accounts.set(response.content);
        this.page.set(response.number);
        this.totalPages.set(response.totalPages);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected canDeactivate(account: AccountDirectoryEntry): boolean {
    return this.isAdmin() && account.active === true && account.role !== 'ADMIN';
  }

  protected confirmDeactivate(): void {
    const account = this.pending();
    if (!account) {
      return;
    }
    this.pending.set(null);
    this.actionError.set(null);
    this.directoryService.deactivateUser(account.id).subscribe({
      next: (updated) =>
        this.accounts.update((list) => list.map((entry) => (entry.id === updated.id ? updated : entry))),
      error: (err: HttpErrorResponse) =>
        this.actionError.set(err.error?.message ?? 'Could not deactivate the account.'),
    });
  }

  protected label(value: string): string {
    return value
      .replace(/^ROLE_/, '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  private errorMessage(err: HttpErrorResponse): string {
    return err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load accounts.');
  }
}

