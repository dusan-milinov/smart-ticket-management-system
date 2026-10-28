export interface Ticket {
    ticketId: number;
    ticketNumber: string;
    title: string;
    description: string;
    ticketStatus: string;
    ticketPriority: string;
    resolutionSummary: string | null;
    createdAt: string;
    updatedAt: string;
    creatorId: number;
    creatorEmail: string;
    assigneeId: number | null;
    assigneeEmail: string | null;
    ticketType: string;
}