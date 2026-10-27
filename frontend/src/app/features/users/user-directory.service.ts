import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api';
import { Page } from '../../shared/models/page.model';
import { Ticket } from '../tickets/models/ticket.model';
import { TicketFilters, ticketParams } from '../tickets/ticket-presentation';
import { AccountDirectoryEntry } from './account-directory-entry.model';

@Service()
export class UserDirectoryService {
  private readonly http = inject(HttpClient);

  getAllUsers(page = 0, size = 10): Observable<Page<AccountDirectoryEntry>> {
    return this.getPage('/admin/users', page, size);
  }

  getSupportAgents(page = 0, size = 10): Observable<Page<AccountDirectoryEntry>> {
    return this.getPage('/admin/support-agents', page, size);
  }

  getUser(id: number): Observable<AccountDirectoryEntry> {
    return this.http.get<AccountDirectoryEntry>(`${API_BASE_URL}/users/${id}`, { withCredentials: true });
  }

  getUserTickets(id: number, page = 0, size = 10, filters: TicketFilters = {}): Observable<Page<Ticket>> {
    return this.http.get<Page<Ticket>>(`${API_BASE_URL}/users/${id}/tickets`, {
      params: ticketParams(page, size, filters), withCredentials: true,
    });
  }

  createSupportAgent(email: string, password: string): Observable<AccountDirectoryEntry> {
    return this.http.post<AccountDirectoryEntry>(
      `${API_BASE_URL}/admin/support-agents`,
      { email, password },
      { withCredentials: true },
    );
  }

  /** Admin only; the backend rejects admin targets, self-deactivation and agents with unresolved tickets. */
  deactivateUser(id: number): Observable<AccountDirectoryEntry> {
    return this.http.patch<AccountDirectoryEntry>(
      `${API_BASE_URL}/admin/users/${id}/deactivate`,
      null,
      { withCredentials: true },
    );
  }

  private getPage<T = AccountDirectoryEntry>(path: string, page: number, size: number): Observable<Page<T>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<T>>(`${API_BASE_URL}${path}`, { params, withCredentials: true });
  }
}
