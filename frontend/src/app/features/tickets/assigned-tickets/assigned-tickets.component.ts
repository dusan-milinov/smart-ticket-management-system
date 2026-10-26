import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { Ticket } from '../models/ticket.model';
import { TicketService } from '../services/ticket.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-assigned-tickets',
  imports: [DatePipe, FormsModule, PageHeaderComponent, PaginationComponent],
  styleUrls: ['../ticket-list.css', './assigned-tickets.component.css'],
  templateUrl: './assigned-tickets.component.html',
  host: { '(document:keydown.escape)': 'closeResolveDialog()' },
})
export class AssignedTicketsComponent implements OnInit {
  private readonly ticketService = inject(TicketService);

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ticketToResolve = signal<Ticket | null>(null);
  protected readonly resolution = signal('');
  protected readonly resolving = signal(false);
  protected readonly resolveError = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.page.set(page);
    this.loading.set(true);
    this.error.set(null);

    this.ticketService.getAssignedTickets(page, PAGE_SIZE).subscribe({
      next: (result) => {
        this.tickets.set(result.content);
        this.page.set(result.number);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.errorMessage(err, 'Could not load your assigned tickets.'));
        this.loading.set(false);
      },
    });
  }

  protected viewDetails(ticket: Ticket): void {
    // TODO: navigate to `/tickets/${ticket.ticketId}` once a ticket details page exists.
  }

  protected canResolve(ticket: Ticket): boolean {
    return !['RESOLVED', 'CANCELLED'].includes(ticket.ticketStatus);
  }

  protected openResolveDialog(ticket: Ticket): void {
    this.resolution.set('');
    this.resolveError.set(null);
    this.ticketToResolve.set(ticket);
  }

  protected closeResolveDialog(): void {
    if (!this.resolving()) {
      this.ticketToResolve.set(null);
    }
  }

  protected confirmResolve(): void {
    const ticket = this.ticketToResolve();
    const summary = this.resolution().trim();
    if (!ticket || this.resolving()) {
      return;
    }
    if (!summary) {
      this.resolveError.set('A resolution message is required.');
      return;
    }

    this.resolving.set(true);
    this.resolveError.set(null);
    this.ticketService.resolveTicket(ticket.ticketId, summary).subscribe({
      next: () => {
        this.resolving.set(false);
        this.ticketToResolve.set(null);
        this.load(this.page());
      },
      error: (err: HttpErrorResponse) => {
        this.resolving.set(false);
        this.resolveError.set(this.errorMessage(err, 'Could not resolve the ticket.'));
      },
    });
  }

  protected label(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  private errorMessage(err: HttpErrorResponse, fallback: string): string {
    return err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? fallback);
  }
}
