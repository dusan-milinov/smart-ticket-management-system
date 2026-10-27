import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Ticket } from '../../tickets/models/ticket.model';
import { ticketLabel } from '../../tickets/ticket-presentation';

@Component({
  selector: 'app-recent-tickets',
  imports: [DatePipe, RouterLink],
  templateUrl: './recent-tickets.component.html',
  styleUrls: ['./recent-tickets.component.css', '../../tickets/ticket-badges.css'],
})
export class RecentTicketsComponent {
  readonly tickets = input.required<Ticket[]>();
  readonly returnTo = input<string | null>(null);

  protected readonly label = ticketLabel;
}
