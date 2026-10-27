import { HttpParams } from '@angular/common/http';
import { TicketPriority } from './models/create-ticket-request.model';

export type TicketStatus = 'IN_QUEUE' | 'FULFILLMENT' | 'RESOLVED' | 'CANCELLED';
export type TicketSort = 'newest' | 'oldest';

export interface TicketFilters {
  status?: TicketStatus | null;
  priority?: TicketPriority | null;
  search?: string;
  sort?: TicketSort;
}

export const TICKET_STATUS_TABS: { value: TicketStatus | null; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'IN_QUEUE', label: 'Pending' },
  { value: 'FULFILLMENT', label: 'In Progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

export function ticketLabel(value: string): string {
  const status = TICKET_STATUS_TABS.find((tab) => tab.value === value);
  if (status) return status.label;
  const text = value.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function ticketParams(page: number, size: number, filters: TicketFilters): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  if (filters.status) params = params.set('status', filters.status);
  if (filters.priority) params = params.set('priority', filters.priority);
  if (filters.search) params = params.set('search', filters.search);
  if (filters.sort) {
    const direction = filters.sort === 'oldest' ? 'asc' : 'desc';
    params = params.append('sort', `createdAt,${direction}`).append('sort', `ticketId,${direction}`);
  }
  return params;
}
