import { inject, Service } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../../core/api';
import { Ticket } from '../../tickets/models/ticket.model';
import { TicketStatus } from '../../tickets/services/ticket.service';
import { TicketPriority } from '../../tickets/models/create-ticket-request.model';

export interface AdminDashboardSummary {
  totalTickets: number;
  ticketsByStatus: Record<TicketStatus, number>;
  openTicketsByPriority: Record<TicketPriority, number>;
  unassignedOpenTickets: number;
  totalAccounts: number;
  regularUsers: number;
  supportAgents: number;
  activeSupportAgents: number;
  recentTickets: Ticket[];
}

@Service()
export class AdminDashboardService {
  private readonly http = inject(HttpClient);

  getSummary() {
    return this.http.get<AdminDashboardSummary>(`${API_BASE_URL}/admin/dashboard`, { withCredentials: true });
  }
}
