import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { Ticket } from '../../tickets/models/ticket.model';
import { TicketService } from '../../tickets/services/ticket.service';
import { TicketFiltersComponent } from '../../tickets/ticket-filters/ticket-filters.component';
import { TicketFilters, ticketLabel } from '../../tickets/ticket-presentation';

const RECENT_TICKET_COUNT = 5;

@Component({
  selector: 'app-user-dashboard',
  imports: [DatePipe, PageHeaderComponent, RouterLink, TicketFiltersComponent],
  styleUrls: ['./user-dashboard.component.css', '../../tickets/ticket-badges.css'],
  templateUrl: './user-dashboard.component.html',
})
export class UserDashboardComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private filters: TicketFilters = { sort: 'newest' };
  protected readonly label = ticketLabel;
  protected readonly listHeading = signal('Recent tickets');

  constructor() {
    this.destroyRef.onDestroy(() => this.request?.unsubscribe());
  }

  protected get filtered(): boolean {
    return !!this.filters.status || !!this.filters.priority || !!this.filters.search;
  }

  protected filtersChanged(filters: TicketFilters): void {
    this.filters = filters;
    this.listHeading.set(this.filtered ? 'Matching tickets' : filters.sort === 'oldest' ? 'Oldest tickets' : 'Recent tickets');
    this.loadRecentTickets();
  }

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly totalTickets = signal<number | null>(null);
  protected readonly totalError = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadTicketTotal();
    this.loadRecentTickets();
  }

  protected loadTicketTotal(): void {
    this.totalError.set(null);
    this.ticketService.getMyTicketStatusCounts().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (counts) => this.totalTickets.set(Object.values(counts).reduce((sum, count) => sum + count, 0)),
      error: () => this.totalError.set('Could not load the ticket total.'),
    });
  }

  protected loadRecentTickets(): void {
    this.loading.set(true);
    this.error.set(null);

    this.request?.unsubscribe();
    this.request = this.ticketService.getMyTickets(0, RECENT_TICKET_COUNT, this.filters).subscribe({
      next: (result) => {
        this.tickets.set(result.content);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 0 ? 'Cannot reach the server.' : 'Could not load your tickets.');
        this.loading.set(false);
      },
    });
  }
}
