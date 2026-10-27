import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api';
import { Page } from '../models/page.model';
import { Ticket } from '../models/ticket.model';
import { CreateTicketRequest } from '../models/create-ticket-request.model';
import { TicketEvent } from '../models/ticket-event.model';

@Service()
export class TicketService {

    private readonly http = inject(HttpClient);

    // The backend defaults to size 5, newest first (sorted by ticketId descending).
    getMyTickets(page = 0, size = 5): Observable<Page<Ticket>> {
        const params = new HttpParams().set('page', page).set('size', size);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/my`, { params, withCredentials: true });
    }

    getAssignedTickets(page = 0, size = 5): Observable<Page<Ticket>> {
        const params = new HttpParams().set('page', page).set('size', size);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/assigned`, { params, withCredentials: true });
    }

    resolveTicket(ticketId: number, summary: string): Observable<Ticket> {
        const params = new HttpParams().set('ticketID', ticketId).set('summary', summary);
        return this.http.patch<Ticket>(`${API_BASE_URL}/tickets/resolve`, null, { params, withCredentials: true });
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
}
