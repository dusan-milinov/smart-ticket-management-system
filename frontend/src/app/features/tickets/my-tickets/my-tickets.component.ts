import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { Ticket } from '../models/ticket.model';
import { TicketService } from '../services/ticket.service';

const PAGE_SIZE = 10;

// Mirrors Ticket.cancel() in the backend, which rejects resolved and cancelled tickets.
const NON_CANCELLABLE_STATUSES = ['RESOLVED', 'CANCELLED'];

@Component({
  imports: [DatePipe, PageHeaderComponent, PaginationComponent],
  selector: 'app-my-tickets',
  styleUrl: '../ticket-list.css',
  templateUrl: './my-tickets.component.html',
  host: { '(document:keydown.escape)': 'closeCancelDialog()' },
})
export class MyTicketsComponent implements OnInit {
  private readonly ticketService = inject(TicketService);

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly ticketToCancel = signal<Ticket | null>(null);
  protected readonly cancelling = signal(false);
  protected readonly cancelError = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.ticketService.getMyTickets(page, PAGE_SIZE).subscribe({
      next: (result) => {
        this.tickets.set(result.content);
        this.page.set(result.number);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.errorMessage(err, 'Could not load your tickets.'));
        this.loading.set(false);
      },
    });
  }

  protected canCancel(ticket: Ticket): boolean {
    return !NON_CANCELLABLE_STATUSES.includes(ticket.ticketStatus);
  }

  protected viewDetails(ticket: Ticket): void {
    // TODO: navigate to `/tickets/${ticket.ticketId}` once a ticket details page exists.
    // The backend already provides GET /tickets/{ticketId}.
  }

  protected openCancelDialog(ticket: Ticket): void {
    this.cancelError.set(null);
    this.ticketToCancel.set(ticket);
  }

  protected closeCancelDialog(): void {
    if (!this.cancelling()) {
      this.ticketToCancel.set(null);
    }
  }

  protected confirmCancel(): void {
    const ticket = this.ticketToCancel();
    if (!ticket || this.cancelling()) {
      return;
    }

    this.cancelling.set(true);
    this.cancelError.set(null);

    this.ticketService.cancelTicket(ticket.ticketId).subscribe({
      next: () => {
        this.cancelling.set(false);
        this.ticketToCancel.set(null);
        this.load(this.page());
      },
      error: (err: HttpErrorResponse) => {
        this.cancelling.set(false);
        this.cancelError.set(this.errorMessage(err, 'Could not cancel the ticket.'));
      },
    });
  }

  // IN_QUEUE -> "In queue"
  protected label(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  private errorMessage(err: HttpErrorResponse, fallback: string): string {
    return err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? fallback);
  }
}
