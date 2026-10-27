import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api';
import { Page } from '../../shared/models/page.model';
import { Ticket } from '../tickets/models/ticket.model';
import { AccountDirectoryEntry } from './account-directory-entry.model';

@Service()
export class UserDirectoryService {
  private readonly http = inject(HttpClient);

  getNonAdminUsers(page = 0, size = 10): Observable<Page<AccountDirectoryEntry>> {
    return this.getPage('/users', page, size);
  }

  getAllUsers(page = 0, size = 10): Observable<Page<AccountDirectoryEntry>> {
    return this.getPage('/admin/users', page, size);
  }

  getSupportAgents(page = 0, size = 10): Observable<Page<AccountDirectoryEntry>> {
    return this.getPage('/admin/support-agents', page, size);
  }

  getUser(id: number): Observable<AccountDirectoryEntry> {
    return this.http.get<AccountDirectoryEntry>(`${API_BASE_URL}/users/${id}`, { withCredentials: true });
  }

  getUserTickets(id: number, page = 0, size = 10): Observable<Page<Ticket>> {
    return this.getPage(`/users/${id}/tickets`, page, size);
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
