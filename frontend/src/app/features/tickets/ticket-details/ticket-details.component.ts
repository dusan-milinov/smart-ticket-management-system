import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Ticket } from '../models/ticket.model';
import { TicketService } from '../services/ticket.service';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';


@Component({
  imports: [DatePipe, RouterLink, PageHeaderComponent],
  selector: 'app-ticket-details',
  styleUrl: './ticket-details.component.css',
  templateUrl: './ticket-details.component.html',
})
export class TicketDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ticketService = inject(TicketService);

  protected readonly ticket = signal<Ticket | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.error.set('Invalid ticket id.');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.ticketService.getTicketById(id).subscribe({
      next: (ticket) => {
        this.ticket.set(ticket);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load the ticket.'));
        this.loading.set(false);
      },
    });
  }

  // IN_QUEUE -> "In queue"
  protected label(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}