export interface TicketComment {
  id: number;
  content: string;
  createdAt: string;
  authorId: number;
  authorEmail: string;
  authorRole: 'USER' | 'SUPPORT_AGENT' | 'ADMIN';
  ticketId: number;
}
