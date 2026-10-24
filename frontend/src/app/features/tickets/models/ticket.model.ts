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
    assigneeId: number | null;
    ticketType: string;
}