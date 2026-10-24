import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../../core/api';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type TicketType = 'INCIDENT' | 'SOFTWARE_REQUEST';

export interface CreateTicketRequest {
  title: string;
  description: string;
  ticketPriority: TicketPriority;
  ticketType: TicketType;
}

export interface TicketResponse extends CreateTicketRequest {
  ticketId: number;
  ticketNumber: string;
  ticketStatus: 'IN_QUEUE' | 'FULFILLMENT' | 'RESOLVED' | 'CANCELLED';
  resolutionSummary: string | null;
  createdAt: string;
  updatedAt: string;
  creatorId: number;
  assigneeId: number | null;
}

@Service()
export class TicketService {
  private readonly http = inject(HttpClient);

  createTicket(request: CreateTicketRequest) {
    return this.http.post<TicketResponse>(`${API_BASE_URL}/tickets/add`, request, {
      withCredentials: true,
    });
  }
}
