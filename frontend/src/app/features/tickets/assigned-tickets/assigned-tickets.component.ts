import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { Ticket } from '../models/ticket.model';
import { TicketService } from '../services/ticket.service';
import { TicketFiltersComponent } from '../ticket-filters/ticket-filters.component';
import { TicketFilters, TicketStatus, ticketLabel } from '../ticket-presentation';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-assigned-tickets',
  imports: [DatePipe, PageHeaderComponent, PaginationComponent, RouterLink, TicketFiltersComponent],
  styleUrls: ['../ticket-list.css', '../ticket-badges.css'],
  templateUrl: './assigned-tickets.component.html',
})
export class AssignedTicketsComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private countsRequest?: Subscription;
  private filters: TicketFilters = { sort: 'newest' };
  protected readonly label = ticketLabel;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.request?.unsubscribe();
      this.countsRequest?.unsubscribe();
    });
  }

  protected filtersChanged(filters: TicketFilters): void {
    const refreshCounts = filters.priority !== this.filters.priority || filters.search !== this.filters.search;
    this.filters = filters;
    if (refreshCounts) this.loadCounts();
    this.load(0);
  }

  protected get filtered(): boolean {
    return !!this.filters.status || !!this.filters.priority || !!this.filters.search;
  }

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly counts = signal<Partial<Record<TicketStatus, number>> | null>(null);
  protected readonly countsError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadCounts();
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.request?.unsubscribe();
    this.request = this.ticketService.getAssignedTickets(page, PAGE_SIZE, this.filters).subscribe({
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

  protected loadCounts(): void {
    this.countsError.set(null);
    this.countsRequest?.unsubscribe();
    this.countsRequest = this.ticketService.getAssignedTicketStatusCounts(this.filters).subscribe({
      next: (counts) => this.counts.set(counts),
      error: (err: HttpErrorResponse) => {
        this.counts.set(null);
        this.countsError.set(this.errorMessage(err, 'Could not load ticket status counts.'));
      },
    });
  }

  private errorMessage(err: HttpErrorResponse, fallback: string): string {
    return err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? fallback);
  }
}
