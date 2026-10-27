import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api';
import { Page } from '../models/page.model';
import { Ticket } from '../models/ticket.model';
import { CreateTicketRequest } from '../models/create-ticket-request.model';
import { TicketEvent } from '../models/ticket-event.model';
import { TicketComment } from '../models/ticket-comment.model';
import { TicketPriority } from '../models/create-ticket-request.model';

export type TicketStatus = 'IN_QUEUE' | 'FULFILLMENT' | 'RESOLVED' | 'CANCELLED';

export interface MyTicketFilters {
    status?: TicketStatus | null;
    priority?: TicketPriority | null;
    search?: string;
}

@Service()
export class TicketService {

    private readonly http = inject(HttpClient);

    // The backend defaults to size 5, newest first (sorted by ticketId descending).
    getMyTickets(page = 0, size = 5, filters: MyTicketFilters = {}): Observable<Page<Ticket>> {
        let params = new HttpParams().set('page', page).set('size', size);
        if (filters.status) params = params.set('status', filters.status);
        if (filters.priority) params = params.set('priority', filters.priority);
        if (filters.search) params = params.set('search', filters.search);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/my`, { params, withCredentials: true });
    }

    // Totals per status; the priority and search filters apply, the status filter does not.
    getMyTicketStatusCounts(filters: Pick<MyTicketFilters, 'priority' | 'search'> = {}): Observable<Partial<Record<TicketStatus, number>>> {
        let params = new HttpParams();
        if (filters.priority) params = params.set('priority', filters.priority);
        if (filters.search) params = params.set('search', filters.search);
        return this.http.get<Partial<Record<TicketStatus, number>>>(`${API_BASE_URL}/tickets/my/status-counts`, { params, withCredentials: true });
    }

    getAssignedTickets(page = 0, size = 5): Observable<Page<Ticket>> {
        const params = new HttpParams().set('page', page).set('size', size);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/assigned`, { params, withCredentials: true });
    }

    resolveTicket(ticketId: number, summary: string): Observable<Ticket> {
        const params = new HttpParams().set('ticketID', ticketId).set('summary', summary);
        return this.http.patch<Ticket>(`${API_BASE_URL}/tickets/resolve`, null, { params, withCredentials: true });
    }

    reassignTicket(ticketId: number, assigneeId: number): Observable<Ticket> {
        const params = new HttpParams().set('ticketID', ticketId).set('assigneeId', assigneeId);
        return this.http.patch<Ticket>(`${API_BASE_URL}/tickets/reassign`, null, { params, withCredentials: true });
    }

    startTicketProcessing(ticketId: number): Observable<Ticket> {
        return this.http.patch<Ticket>(`${API_BASE_URL}/tickets/${ticketId}/start`, null, { withCredentials: true });
    }

    cancelTicket(ticketId: number): Observable<Ticket> {
        const params = new HttpParams().set('ticketID', ticketId);
        return this.http.patch<Ticket>(`${API_BASE_URL}/tickets/cancel`, null, { params, withCredentials: true });
    }

    createTicket(request: CreateTicketRequest) {
        return this.http.post<Ticket>(`${API_BASE_URL}/tickets/add`, request, { withCredentials: true });
    }


    getTicketById(ticketId: number): Observable<Ticket> {
        return this.http.get<Ticket>(`${API_BASE_URL}/tickets/${ticketId}`, { withCredentials: true });
    }

    getTicketEvents(ticketId: number): Observable<TicketEvent[]> {
        return this.http.get<TicketEvent[]>(`${API_BASE_URL}/ticket-events/${ticketId}`, { withCredentials: true });
    }

    getTicketComments(ticketId: number): Observable<TicketComment[]> {
        return this.http.get<TicketComment[]>(`${API_BASE_URL}/comments/${ticketId}`, { withCredentials: true });
    }

    postTicketComment(ticketId: number, content: string): Observable<TicketComment> {
        return this.http.post<TicketComment>(`${API_BASE_URL}/comments`, { ticketId, content }, { withCredentials: true });
    }
}
