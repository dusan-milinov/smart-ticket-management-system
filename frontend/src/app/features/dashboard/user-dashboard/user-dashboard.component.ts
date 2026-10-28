import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Ticket } from '../../tickets/models/ticket.model';
import { TicketService } from '../../tickets/services/ticket.service';
import { TicketStatus } from '../../tickets/ticket-presentation';
import { RecentTicketsComponent } from '../recent-tickets/recent-tickets.component';

const RECENT_TICKET_COUNT = 5;

@Component({
  selector: 'app-user-dashboard',
  imports: [RecentTicketsComponent, RouterLink],
  styleUrl: './user-dashboard.component.css',
  templateUrl: './user-dashboard.component.html',
})
export class UserDashboardComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  protected readonly statusCards: { status: TicketStatus; title: string; caption: string; className: string }[] = [
    { status: 'IN_QUEUE', title: 'Pending', caption: 'Waiting for support', className: 'pending' },
    { status: 'FULFILLMENT', title: 'In progress', caption: 'Being worked on', className: 'progress' },
    { status: 'RESOLVED', title: 'Resolved', caption: 'Completed requests', className: 'resolved' },
    { status: 'CANCELLED', title: 'Cancelled', caption: 'Closed requests', className: 'cancelled' },
  ];

  constructor() {
    this.destroyRef.onDestroy(() => this.request?.unsubscribe());
  }

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly statusCounts = signal<Partial<Record<TicketStatus, number>> | null>(null);
  protected readonly totalTickets = signal<number | null>(null);
  protected readonly totalError = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected countFor(status: TicketStatus): number | null {
    const counts = this.statusCounts();
    return counts ? counts[status] ?? 0 : null;
  }

  protected get openTickets(): number | null {
    const counts = this.statusCounts();
    return counts ? (counts.IN_QUEUE ?? 0) + (counts.FULFILLMENT ?? 0) : null;
  }

  ngOnInit(): void {
    this.loadTicketTotal();
    this.loadRecentTickets();
  }

  protected loadTicketTotal(): void {
    this.totalError.set(null);
    this.ticketService.getMyTicketStatusCounts().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (counts) => {
        this.statusCounts.set(counts);
        this.totalTickets.set(Object.values(counts).reduce((sum, count) => sum + count, 0));
      },
      error: () => this.totalError.set('Could not load the ticket total.'),
    });
  }

  protected loadRecentTickets(): void {
    this.loading.set(true);
    this.error.set(null);

    this.request?.unsubscribe();
    this.request = this.ticketService.getMyTickets(0, RECENT_TICKET_COUNT, { sort: 'newest' }).subscribe({
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
