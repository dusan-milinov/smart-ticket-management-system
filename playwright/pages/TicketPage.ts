import { APIRequestContext } from '@playwright/test';

export class TicketPage {

  constructor(
    private readonly request: APIRequestContext
  ) {}

  async createTicket(
    token: string,
    title: string,
    description: string
  ) {

    return this.request.post(
      '/tickets/add',
      {
        headers: {
          Authorization:
            `Bearer ${token}`
        },

        data: {
          title,
          description,
          ticketPriority: 'HIGH',
          ticketType: 'INCIDENT'
        }
      }
    );
  }

  async getTicket(
    token: string,
    ticketId: number
  ) {

    return this.request.get(
      `/tickets/${ticketId}`,
      {
        headers: {
          Authorization:
            `Bearer ${token}`
        }
      }
    );
  }

  async getTickets(
    token: string
  ) {

    return this.request.get(
      '/tickets',
      {
        headers: {
          Authorization:
            `Bearer ${token}`
        }
      }
    );
  }

  async getTicketsByStatus(
    token: string,
    status: string
  ) {

    return this.request.get(
      `/tickets/status/${status}`,
      {
        headers: {
          Authorization:
            `Bearer ${token}`
        }
      }
    );
  }

  async resolveTicket(
    token: string,
    ticketId: number,
    summary: string
  ) {

    return this.request.patch(
      '/tickets/resolve',
      {
        headers: {
          Authorization:
            `Bearer ${token}`
        },

        params: {
          ticketID: ticketId,
          summary
        }
      }
    );
  }
}