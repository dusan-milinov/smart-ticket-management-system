import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api';
import { Page } from '../models/page.model';
import { Ticket } from '../models/ticket.model';

@Service()
export class TicketService {

    private readonly http = inject(HttpClient);

    // The backend defaults to size 5, newest first (sorted by ticketId descending).
    getMyTickets(page = 0, size = 5): Observable<Page<Ticket>> {
        const params = new HttpParams().set('page', page).set('size', size);
        return this.http.get<Page<Ticket>>(`${API_BASE_URL}/tickets/my`, { params, withCredentials: true });
    }

    cancelTicket(ticketId: number): Observable<Ticket> {
        const params = new HttpParams().set('ticketID', ticketId);
        return this.http.patch<Ticket>(`${API_BASE_URL}/tickets/cancel`, null, { params, withCredentials: true });
    }
}
