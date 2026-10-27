import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Ticket } from '../../tickets/models/ticket.model';

@Component({
  selector: 'app-recent-tickets',
  imports: [DatePipe, RouterLink],
  templateUrl: './recent-tickets.component.html',
  styleUrl: './recent-tickets.component.css',
})
export class RecentTicketsComponent {
  readonly tickets = input.required<Ticket[]>();
  readonly returnTo = input<string | null>(null);

  protected label(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
