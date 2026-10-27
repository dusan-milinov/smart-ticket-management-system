import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ROLES } from '../../core/roles';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { Ticket } from '../tickets/models/ticket.model';
import { AccountDirectoryEntry } from './account-directory-entry.model';
import { UserDirectoryService } from './user-directory.service';

const PAGE_SIZE = 10;

@Component({
  imports: [PageHeaderComponent, PaginationComponent, RouterLink],
  selector: 'app-user-details',
  styleUrl: './user-details.component.css',
  templateUrl: './user-details.component.html',
})
export class UserDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private readonly directoryService = inject(UserDirectoryService);

  private userId = 0;

  protected readonly isAdmin = computed(() => this.authService.user()?.role === ROLES.ADMIN);
  protected readonly account = signal<AccountDirectoryEntry | null>(null);
  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly confirming = signal(false);

  protected readonly canDeactivate = computed(() => {
    const account = this.account();
    return this.isAdmin() && account?.active === true && account.role !== 'ADMIN';
  });

  ngOnInit(): void {
    this.userId = Number(this.route.snapshot.paramMap.get('id'));
    this.directoryService.getUser(this.userId).subscribe({
      next: (account) => {
        this.account.set(account);
        this.loadTickets(0);
      },
      error: (err: HttpErrorResponse) => this.fail(err),
    });
  }

  protected loadTickets(page: number): void {
    this.loading.set(true);
    this.directoryService.getUserTickets(this.userId, page, PAGE_SIZE).subscribe({
      next: (response) => {
        this.tickets.set(response.content);
        this.page.set(response.number);
        this.totalPages.set(response.totalPages);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => this.fail(err),
    });
  }

  protected confirmDeactivate(): void {
    this.confirming.set(false);
    this.actionError.set(null);
    this.directoryService.deactivateUser(this.userId).subscribe({
      next: (updated) => this.account.set(updated),
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

  private fail(err: HttpErrorResponse): void {
    this.error.set(
      err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load the user.'),
    );
    this.loading.set(false);
  }
}
