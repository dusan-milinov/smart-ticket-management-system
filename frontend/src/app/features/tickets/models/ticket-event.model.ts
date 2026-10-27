export type TicketEventType =
  | 'CREATED'
  | 'ASSIGNED'
  | 'REASSIGNED'
  | 'COMMENT_ADDED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'RESOLVED'
  | 'CLOSED';

export interface TicketEvent {
    id: number;
    eventType: TicketEventType;
    oldValue: string | null;
    newValue: string | null;
    createdAt: string;
    ticketId: number;
    userId: number;
}