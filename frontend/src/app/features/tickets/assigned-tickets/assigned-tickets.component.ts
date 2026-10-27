import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { Ticket } from '../models/ticket.model';
import { TicketService } from '../services/ticket.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-assigned-tickets',
  imports: [DatePipe, PageHeaderComponent, PaginationComponent],
  styleUrls: ['../ticket-list.css', './assigned-tickets.component.css'],
  templateUrl: './assigned-tickets.component.html',
})
export class AssignedTicketsComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly router = inject(Router);

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly openingTicketId = signal<number | null>(null);
  protected readonly openError = signal<string | null>(null);
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
    this.openError.set(null);
    if (ticket.ticketStatus !== 'IN_QUEUE') {
      this.router.navigate(['/tickets', ticket.ticketId]);
      return;
    }

    this.openingTicketId.set(ticket.ticketId);
    this.ticketService.startTicketProcessing(ticket.ticketId).subscribe({
      next: (updatedTicket) => {
        this.tickets.update((tickets) => tickets.map((current) =>
          current.ticketId === updatedTicket.ticketId ? updatedTicket : current,
        ));
        this.openingTicketId.set(null);
        this.router.navigate(['/tickets', ticket.ticketId]);
      },
      error: (err: HttpErrorResponse) => {
        this.openingTicketId.set(null);
        this.openError.set(this.errorMessage(err, 'Could not open the ticket.'));
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
