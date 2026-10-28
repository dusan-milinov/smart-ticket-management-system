import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { AccountDirectoryEntry } from './account-directory-entry.model';
import { UserDirectoryService } from './user-directory.service';

const PAGE_SIZE = 10;

type DirectoryList = 'users' | 'agents';

@Component({
  imports: [PageHeaderComponent, PaginationComponent, RouterLink],
  selector: 'app-user-directory',
  host: { '(document:keydown.escape)': 'closeDialogs()' },
  styleUrl: './user-directory.component.css',
  templateUrl: './user-directory.component.html',
})
export class UserDirectoryComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly directoryService = inject(UserDirectoryService);

  protected readonly list = signal<DirectoryList>('users');
  protected readonly accounts = signal<AccountDirectoryEntry[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pending = signal<AccountDirectoryEntry | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly deactivating = signal(false);

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('list') === 'agents') {
      this.list.set('agents');
    }
    this.load(0);
  }

  protected selectList(list: DirectoryList): void {
    if (this.list() === list) {
      return;
    }
    this.list.set(list);
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set(null);

    const result = this.list() === 'agents'
      ? this.directoryService.getSupportAgents(page, PAGE_SIZE)
      : this.directoryService.getAllUsers(page, PAGE_SIZE);

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
    return account.active === true && account.role !== 'ADMIN';
  }

  protected isDeactivated(account: AccountDirectoryEntry): boolean {
    return account.active === false;
  }

  protected confirmDeactivate(): void {
    const account = this.pending();
    if (!account || !this.canDeactivate(account) || this.deactivating()) {
      return;
    }

    this.deactivating.set(true);
    this.actionError.set(null);
    this.directoryService.deactivateUser(account.id).subscribe({
      next: (updated) => {
        this.accounts.update((accounts) => accounts.map((entry) => entry.id === updated.id ? updated : entry));
        this.pending.set(null);
        this.deactivating.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.actionError.set(
          err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not deactivate the account.'),
        );
        this.pending.set(null);
        this.deactivating.set(false);
      },
    });
  }

  protected closeDialogs(): void {
    if (this.deactivating()) {
      return;
    }
    this.pending.set(null);
    this.actionError.set(null);
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
