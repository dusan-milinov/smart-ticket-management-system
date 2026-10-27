import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ROLES } from '../../core/roles';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { Ticket } from '../tickets/models/ticket.model';
import { TicketService } from '../tickets/services/ticket.service';
import { TicketFiltersComponent } from '../tickets/ticket-filters/ticket-filters.component';
import { TicketFilters, ticketLabel } from '../tickets/ticket-presentation';
import { AccountDirectoryEntry } from './account-directory-entry.model';
import { UserDirectoryService } from './user-directory.service';

const PAGE_SIZE = 10;

@Component({
  imports: [DatePipe, PageHeaderComponent, PaginationComponent, RouterLink, TicketFiltersComponent],
  selector: 'app-user-details',
  host: { '(document:keydown.escape)': 'closeDialogs()' },
  styleUrls: ['../tickets/ticket-list.css', './user-details.component.css', '../tickets/ticket-badges.css'],
  templateUrl: './user-details.component.html',
})
export class UserDetailsComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private readonly directoryService = inject(UserDirectoryService);
  private readonly ticketService = inject(TicketService);

  private userId = 0;
  private filters: TicketFilters = { sort: 'newest' };
  private ticketsRequest?: Subscription;
  protected readonly ticketLabel = ticketLabel;

  protected readonly isAdmin = computed(() => this.authService.user()?.role === ROLES.ADMIN);
  protected readonly account = signal<AccountDirectoryEntry | null>(null);
  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly agents = signal<AccountDirectoryEntry[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ticketsError = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly confirming = signal(false);
  protected readonly reassigning = signal<Ticket | null>(null);
  protected readonly selectedAgentId = signal<number | null>(null);
  protected readonly reassignSaving = signal(false);
  protected readonly reassignError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  private noticeTimer?: ReturnType<typeof setTimeout>;

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
        if (this.isAdmin()) {
          this.directoryService.getSupportAgents(0, 100).subscribe({
            next: (response) => this.agents.set(response.content.filter((agent) => agent.active !== false)),
            error: () => this.actionError.set('Could not load support agents.'),
          });
        }
      },
      error: (err: HttpErrorResponse) => this.fail(err),
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.noticeTimer);
    this.ticketsRequest?.unsubscribe();
  }

  protected filtersChanged(filters: TicketFilters): void {
    this.filters = filters;
    this.loadTickets(0);
  }

  protected loadTickets(page: number): void {
    this.loading.set(true);
    this.ticketsError.set(null);
    this.ticketsRequest?.unsubscribe();
    this.ticketsRequest = this.directoryService.getUserTickets(this.userId, page, PAGE_SIZE, this.filters).subscribe({
      next: (response) => {
        this.tickets.set(response.content);
        this.page.set(response.number);
        this.totalPages.set(response.totalPages);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.ticketsError.set(err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load tickets.'));
        this.loading.set(false);
      },
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

  protected canReassign(ticket: Ticket): boolean {
    return this.isAdmin() && (ticket.ticketStatus === 'IN_QUEUE' || ticket.ticketStatus === 'FULFILLMENT');
  }

  protected agentsFor(ticket: Ticket): AccountDirectoryEntry[] {
    return this.agents().filter((agent) => agent.id !== ticket.assigneeId);
  }

  protected agentEmail(id: number | null): string {
    if (id === null) {
      return 'Unassigned';
    }
    return this.agents().find((agent) => agent.id === id)?.email ?? `Agent #${id}`;
  }

  protected closeDialogs(): void {
    this.confirming.set(false);
    this.closeReassign();
  }

  protected openReassign(ticket: Ticket): void {
    this.reassigning.set(ticket);
    this.selectedAgentId.set(null);
    this.reassignError.set(null);
  }

  protected closeReassign(): void {
    if (!this.reassignSaving()) {
      this.reassigning.set(null);
    }
  }

  protected selectAgent(value: string): void {
    this.selectedAgentId.set(value ? Number(value) : null);
  }

  protected confirmReassign(): void {
    const ticket = this.reassigning();
    const assigneeId = this.selectedAgentId();
    if (!ticket || assigneeId === null || this.reassignSaving()) {
      return;
    }
    this.reassignSaving.set(true);
    this.reassignError.set(null);
    this.ticketService.reassignTicket(ticket.ticketId, assigneeId).subscribe({
      next: () => {
        this.reassignSaving.set(false);
        this.reassigning.set(null);
        this.showNotice(`${ticket.ticketNumber} reassigned to ${this.agentEmail(assigneeId)}.`);
        this.loadTickets(this.page());
      },
      error: (err: HttpErrorResponse) => {
        this.reassignSaving.set(false);
        this.reassignError.set(err.error?.message ?? 'Could not reassign the ticket.');
      },
    });
  }

  private showNotice(message: string): void {
    clearTimeout(this.noticeTimer);
    this.notice.set(message);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 4000);
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
