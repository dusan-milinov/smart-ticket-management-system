import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api';
import { Page } from '../models/page.model';
import { Ticket } from '../models/ticket.model';
import { CreateTicketRequest } from '../models/create-ticket-request.model';
import { TicketEvent } from '../models/ticket-event.model';
import { TicketComment } from '../models/ticket-comment.model';
import { ResolvedTicketDay } from '../models/resolved-ticket-day.model';
import { TicketFilters, TicketStatus, ticketParams } from '../ticket-presentation';
export type { TicketStatus } from '../ticket-presentation';
export type MyTicketFilters = TicketFilters;

@Service()
export class TicketService {

    private readonly http = inject(HttpClient);

    // The backend defaults to size 5, newest first (sorted by ticketId descending).
    getMyTickets(page = 0, size = 5, filters: MyTicketFilters = {}): Observable<Page<Ticket>> {
        const params = ticketParams(page, size, filters);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/my`, { params, withCredentials: true });
    }

    // Totals per status; the priority and search filters apply, the status filter does not.
    getMyTicketStatusCounts(filters: Pick<MyTicketFilters, 'priority' | 'search'> = {}): Observable<Partial<Record<TicketStatus, number>>> {
        let params = new HttpParams();
        if (filters.priority) params = params.set('priority', filters.priority);
        if (filters.search) params = params.set('search', filters.search);
        return this.http.get<Partial<Record<TicketStatus, number>>>(`${API_BASE_URL}/tickets/my/status-counts`, { params, withCredentials: true });
    }

    getAssignedTickets(page = 0, size = 5, filters: TicketFilters = {}): Observable<Page<Ticket>> {
        const params = ticketParams(page, size, filters);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/assigned`, { params, withCredentials: true });
    }

    getAssignedPriorityQueue(): Observable<Ticket[]> {
        return this.http.get<Ticket[]>(`${API_BASE_URL}/tickets/assigned/priority-queue`, { withCredentials: true });
    }

    getAssignedTicketStatusCounts(filters: Pick<TicketFilters, 'priority' | 'search'> = {}): Observable<Partial<Record<TicketStatus, number>>> {
        let params = new HttpParams();
        if (filters.priority) params = params.set('priority', filters.priority);
        if (filters.search) params = params.set('search', filters.search);
        return this.http.get<Partial<Record<TicketStatus, number>>>(`${API_BASE_URL}/tickets/assigned/status-counts`, { params, withCredentials: true });
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

    getMyDailyResolutions(days: 7 | 30): Observable<ResolvedTicketDay[]> {
        const params = new HttpParams().set('days', days);
        return this.http.get<ResolvedTicketDay[]>(`${API_BASE_URL}/ticket-events/my/resolved-daily`, { params, withCredentials: true });
    }

    getTicketComments(ticketId: number): Observable<TicketComment[]> {
        return this.http.get<TicketComment[]>(`${API_BASE_URL}/comments/${ticketId}`, { withCredentials: true });
    }

    postTicketComment(ticketId: number, content: string): Observable<TicketComment> {
        return this.http.post<TicketComment>(`${API_BASE_URL}/comments`, { ticketId, content }, { withCredentials: true });
    }
}
