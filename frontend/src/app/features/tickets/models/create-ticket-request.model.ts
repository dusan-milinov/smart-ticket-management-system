export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type TicketType = 'INCIDENT' | 'SOFTWARE_REQUEST';


export interface CreateTicketRequest {
  title: string;
  description: string;
  ticketPriority: TicketPriority;
  ticketType: TicketType;
}
