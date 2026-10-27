import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { Ticket } from '../../tickets/models/ticket.model';
import { TicketService } from '../../tickets/services/ticket.service';
import { RecentTicketsComponent } from '../recent-tickets/recent-tickets.component';

const RECENT_TICKET_COUNT = 5;

@Component({
  selector: 'app-user-dashboard',
  imports: [PageHeaderComponent, RecentTicketsComponent, RouterLink],
  styleUrl: './user-dashboard.component.css',
  templateUrl: './user-dashboard.component.html',
})
export class UserDashboardComponent implements OnInit {
  private readonly ticketService = inject(TicketService);

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly totalTickets = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadRecentTickets();
  }

  protected loadRecentTickets(): void {
    this.loading.set(true);
    this.error.set(null);

    this.ticketService.getMyTickets(0, RECENT_TICKET_COUNT).subscribe({
      next: (result) => {
        this.tickets.set(result.content);
        this.totalTickets.set(result.totalElements);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 0 ? 'Cannot reach the server.' : 'Could not load your tickets.');
        this.loading.set(false);
      },
    });
  }
}
