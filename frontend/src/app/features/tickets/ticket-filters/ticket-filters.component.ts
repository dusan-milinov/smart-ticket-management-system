import { Component, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, Subject } from 'rxjs';
import {
  TicketFilters, TicketStatus, TICKET_PRIORITIES, TICKET_STATUS_TABS, ticketLabel,
} from '../ticket-presentation';

@Component({
  selector: 'app-ticket-filters',
  templateUrl: './ticket-filters.component.html',
  styleUrl: './ticket-filters.component.css',
})
export class TicketFiltersComponent {
  readonly counts = input<Partial<Record<TicketStatus, number>> | null>(null);
  readonly filtersChange = output<TicketFilters>();
  protected readonly tabs = TICKET_STATUS_TABS;
  protected readonly priorities = TICKET_PRIORITIES;
  protected readonly label = ticketLabel;
  protected readonly filters = signal<TicketFilters>({ sort: 'newest' });
  protected readonly searchText = signal('');
  private readonly searchInput = new Subject<string>();

  constructor() {
    this.searchInput.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) => {
      const search = value.trim();
      if (search !== (this.filters().search ?? '')) this.update({ search });
    });
  }

  protected update(change: TicketFilters): void {
    this.filters.update((filters) => ({ ...filters, ...change }));
    this.filtersChange.emit(this.filters());
  }

  protected selectPriority(event: Event): void {
    if (event.target instanceof HTMLSelectElement) {
      const selected = event.target.value;
      this.update({ priority: TICKET_PRIORITIES.find((value) => value === selected) ?? null });
    }
  }

  protected selectSort(event: Event): void {
    if (event.target instanceof HTMLSelectElement) {
      this.update({ sort: event.target.value === 'oldest' ? 'oldest' : 'newest' });
    }
  }

  protected onSearch(event: Event): void {
    if (event.target instanceof HTMLInputElement) {
      this.searchText.set(event.target.value);
      this.searchInput.next(event.target.value);
    }
  }

  protected clearSearch(): void {
    this.searchText.set('');
    this.searchInput.next('');
    if (this.filters().search) this.update({ search: '' });
  }

  protected count(status: TicketStatus | null): number | null {
    const counts = this.counts();
    if (!counts) return null;
    return status ? counts[status] ?? 0 : Object.values(counts).reduce((sum, count) => sum + count, 0);
  }
}
