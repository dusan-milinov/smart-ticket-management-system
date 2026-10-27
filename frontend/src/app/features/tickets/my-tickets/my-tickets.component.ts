import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, Subject, Subscription } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TicketPriority } from '../models/create-ticket-request.model';
import { Ticket } from '../models/ticket.model';
import { TicketService, TicketStatus } from '../services/ticket.service';
import { RouterLink } from '@angular/router';

const PAGE_SIZE = 10;
const SEARCH_DEBOUNCE_MS = 300;

// Mirrors Ticket.cancel() in the backend, which rejects resolved and cancelled tickets.
const NON_CANCELLABLE_STATUSES = ['RESOLVED', 'CANCELLED'];

// Backend TicketStatus / TicketPriority enum values.
const STATUS_TABS: { value: TicketStatus | null; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'IN_QUEUE', label: 'Pending' },
  { value: 'FULFILLMENT', label: 'In Progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'CANCELLED', label: 'Cancelled' },
];
const PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

@Component({
  imports: [DatePipe, PageHeaderComponent, PaginationComponent, RouterLink],
  selector: 'app-my-tickets',
  styleUrls: ['../ticket-list.css', './my-tickets.component.css'],
  templateUrl: './my-tickets.component.html',
  host: { '(document:keydown.escape)': 'closeCancelDialog()' },
})
export class MyTicketsComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchInput = new Subject<string>();
  private ticketsRequest?: Subscription;
  private countsRequest?: Subscription;

  protected readonly statusTabs = STATUS_TABS;
  protected readonly priorities = PRIORITIES;

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly status = signal<TicketStatus | null>(null);
  protected readonly priority = signal<TicketPriority | null>(null);
  protected readonly searchText = signal('');
  // The debounced term actually sent to the API.
  protected readonly search = signal('');
  protected readonly counts = signal<Partial<Record<TicketStatus, number>> | null>(null);

  protected readonly ticketToCancel = signal<Ticket | null>(null);
  protected readonly cancelling = signal(false);
  protected readonly cancelError = signal<string | null>(null);

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.ticketsRequest?.unsubscribe();
      this.countsRequest?.unsubscribe();
    });
    this.searchInput
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe((term) => {
        const trimmed = term.trim();
        if (trimmed === this.search()) return;
        this.search.set(trimmed);
        this.filtersChanged(true);
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
    this.ticketsRequest = this.ticketService.getMyTickets(page, PAGE_SIZE, this.filters()).subscribe({
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

  protected selectStatus(status: TicketStatus | null): void {
    if (status === this.status()) return;
    this.status.set(status);
    this.filtersChanged(false);
  }

  protected selectPriority(value: string): void {
    this.priority.set(PRIORITIES.find((priority) => priority === value) ?? null);
    this.filtersChanged(true);
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    this.searchInput.next(value);
  }

  protected clearSearch(): void {
    this.searchText.set('');
    this.searchInput.next('');
    if (!this.search()) return;
    this.search.set('');
    this.filtersChanged(true);
  }

  protected get filtered(): boolean {
    return !!this.status() || !!this.priority() || !!this.search();
  }

  // Counts follow the search and priority filters but not the selected tab.
  private filtersChanged(refreshCounts: boolean): void {
    if (refreshCounts) this.loadCounts();
    this.load(0);
  }

  private filters() {
    return { status: this.status(), priority: this.priority(), search: this.search() };
  }

  private loadCounts(): void {
    this.countsRequest?.unsubscribe();
    this.countsRequest = this.ticketService
      .getMyTicketStatusCounts({ priority: this.priority(), search: this.search() })
      .subscribe({
        next: (counts) => this.counts.set(counts),
        error: () => this.counts.set(null),
      });
  }

  protected tabCount(status: TicketStatus | null): number | null {
    const counts = this.counts();
    if (!counts) return null;
    if (status) return counts[status] ?? 0;
    return Object.values(counts).reduce((sum, count) => sum + count, 0);
  }

  protected canCancel(ticket: Ticket): boolean {
    return !NON_CANCELLABLE_STATUSES.includes(ticket.ticketStatus);
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
        this.loadCounts();
        this.load(this.page());
      },
      error: (err: HttpErrorResponse) => {
        this.cancelling.set(false);
        this.cancelError.set(this.errorMessage(err, 'Could not cancel the ticket.'));
      },
    });
  }

  protected statusLabel(value: string): string {
    return STATUS_TABS.find((tab) => tab.value === value)?.label ?? this.label(value);
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
