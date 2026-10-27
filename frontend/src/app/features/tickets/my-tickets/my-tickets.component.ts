import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TicketFiltersComponent } from '../ticket-filters/ticket-filters.component';
import { TicketFilters, ticketLabel } from '../ticket-presentation';
import { Ticket } from '../models/ticket.model';
import { TicketService, TicketStatus } from '../services/ticket.service';
import { RouterLink } from '@angular/router';

const PAGE_SIZE = 10;

@Component({
  imports: [DatePipe, PageHeaderComponent, PaginationComponent, RouterLink, TicketFiltersComponent],
  selector: 'app-my-tickets',
  styleUrls: ['../ticket-list.css', '../ticket-badges.css'],
  templateUrl: './my-tickets.component.html',
})
export class MyTicketsComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private ticketsRequest?: Subscription;
  private countsRequest?: Subscription;

  protected readonly label = ticketLabel;
  private filters: TicketFilters = { sort: 'newest' };

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly counts = signal<Partial<Record<TicketStatus, number>> | null>(null);

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.ticketsRequest?.unsubscribe();
      this.countsRequest?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.loadCounts();
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.ticketsRequest?.unsubscribe();
    this.ticketsRequest = this.ticketService.getMyTickets(page, PAGE_SIZE, this.filters).subscribe({
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

  protected get filtered(): boolean {
    return !!this.filters.status || !!this.filters.priority || !!this.filters.search;
  }

  protected filtersChanged(filters: TicketFilters): void {
    const refreshCounts = filters.priority !== this.filters.priority || filters.search !== this.filters.search;
    this.filters = filters;
    if (refreshCounts) this.loadCounts();
    this.load(0);
  }

  private loadCounts(): void {
    this.countsRequest?.unsubscribe();
    this.countsRequest = this.ticketService
      .getMyTicketStatusCounts(this.filters)
      .subscribe({
        next: (counts) => this.counts.set(counts),
        error: () => this.counts.set(null),
      });
  }

  private errorMessage(err: HttpErrorResponse, fallback: string): string {
    return err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? fallback);
  }
}
