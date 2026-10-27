import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { ROLES } from '../../../core/roles';
import { TicketEvent, TicketEventType } from '../models/ticket-event.model';
import { Ticket } from '../models/ticket.model';
import { TicketService } from '../services/ticket.service';
import { TicketCommentsComponent } from '../ticket-comments/ticket-comments.component';

type TicketAction = 'cancel' | 'resolve';

// Histories up to this length start expanded.
const EXPANDED_BY_DEFAULT_MAX = 5;

const EVENT_ICONS: Record<TicketEventType, string> = {
  CREATED: '+',
  ASSIGNED: '→',
  REASSIGNED: '↔',
  COMMENT_ADDED: '✎',
  STATUS_CHANGED: '↻',
  PRIORITY_CHANGED: '!',
  RESOLVED: '✓',
  CLOSED: '×',
};

interface TimelineEntry {
  id: number;
  type: TicketEventType;
  icon: string;
  title: string;
  detail: string | null;
  createdAt: string;
}

@Component({
  imports: [DatePipe, FormsModule, RouterLink, TicketCommentsComponent],
  selector: 'app-ticket-details',
  styleUrl: './ticket-details.component.css',
  templateUrl: './ticket-details.component.html',
  host: { '(document:keydown.escape)': 'closeActionDialog()' },
})
export class TicketDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ticketService = inject(TicketService);
  private readonly auth = inject(AuthService);

  protected readonly ticket = signal<Ticket | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly events = signal<TicketEvent[]>([]);
  protected readonly eventsLoading = signal(false);
  protected readonly eventsError = signal<string | null>(null);
  protected readonly historyOpen = signal(true);
  protected readonly action = signal<TicketAction | null>(null);
  protected readonly resolution = signal('');
  protected readonly actionInProgress = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly backLink = computed(() => {
    const role = this.auth.user()?.role;
    if (role === ROLES.ADMIN) {
      const returnTo = this.route.snapshot.queryParamMap.get('returnTo');
      return returnTo && /^\/users\/[1-9]\d*$/.test(returnTo)
        ? { url: returnTo, label: 'Back to user' }
        : { url: '/users', label: 'Back to users' };
    }
    return role === ROLES.SUPPORT_AGENT
      ? { url: '/tickets/assigned', label: 'Back to assigned tickets' }
      : { url: '/tickets', label: 'Back to my tickets' };
  });

  // Newest first; the API does not guarantee any order.
  protected readonly timeline = computed<TimelineEntry[]>(() =>
    [...this.events()]
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id)
      .map((event) => ({
        id: event.id,
        type: event.eventType,
        icon: EVENT_ICONS[event.eventType],
        createdAt: event.createdAt,
        ...this.describe(event),
      })),
  );

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
        this.loadEvents(id);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load the ticket.'));
        this.loading.set(false);
      },
    });
  }

  private loadEvents(ticketId: number): void {
    this.eventsLoading.set(true);
    this.eventsError.set(null);

    this.ticketService.getTicketEvents(ticketId).subscribe({
      next: (events) => {
        this.events.set(events);
        this.historyOpen.set(events.length <= EXPANDED_BY_DEFAULT_MAX);
        this.eventsLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.events.set([]);
        this.eventsError.set(
          err.status === 0
            ? 'Cannot reach the server.'
            : err.status === 403
              ? 'You do not have access to this ticket’s history.'
              : (err.error?.message ?? 'Could not load the event history.'),
        );
        this.eventsLoading.set(false);
      },
    });
  }

  protected toggleHistory(): void {
    this.historyOpen.update((open) => !open);
  }

  protected isAgent(): boolean {
    return this.auth.user()?.role === ROLES.SUPPORT_AGENT;
  }

  protected canCancel(ticket: Ticket): boolean {
    return this.auth.user()?.role === ROLES.USER
      && !['RESOLVED', 'CANCELLED'].includes(ticket.ticketStatus);
  }

  protected canResolve(ticket: Ticket): boolean {
    return this.isAgent() && !['RESOLVED', 'CANCELLED'].includes(ticket.ticketStatus);
  }

  protected openActionDialog(action: TicketAction): void {
    this.action.set(action);
    this.resolution.set('');
    this.actionError.set(null);
  }

  protected updateResolution(event: Event): void {
    if (event.target instanceof HTMLTextAreaElement) {
      this.resolution.set(event.target.value);
    }
  }

  protected closeActionDialog(): void {
    if (!this.actionInProgress()) {
      this.action.set(null);
    }
  }

  protected confirmAction(): void {
    const ticket = this.ticket();
    const action = this.action();
    const summary = this.resolution().trim();
    if (!ticket || !action || this.actionInProgress()) {
      return;
    }
    if (action === 'resolve' && !summary) {
      this.actionError.set('A resolution message is required.');
      return;
    }

    this.actionInProgress.set(true);
    this.actionError.set(null);
    const request = action === 'resolve'
      ? this.ticketService.resolveTicket(ticket.ticketId, summary)
      : this.ticketService.cancelTicket(ticket.ticketId);

    request.subscribe({
      next: (updatedTicket) => {
        this.ticket.set(updatedTicket);
        this.actionInProgress.set(false);
        this.action.set(null);
        this.loadEvents(updatedTicket.ticketId);
      },
      error: (err: HttpErrorResponse) => {
        this.actionInProgress.set(false);
        this.actionError.set(
          err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? `Could not ${action} the ticket.`),
        );
      },
    });
  }

  // IN_QUEUE -> "In queue"
  protected label(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  private describe(event: TicketEvent): { title: string; detail: string | null } {
    switch (event.eventType) {
      case 'CREATED':
        return { title: 'Ticket created', detail: null };
      case 'ASSIGNED':
        return { title: 'Assigned to a support agent', detail: null };
      case 'REASSIGNED':
        return { title: 'Reassigned to another support agent', detail: null };
      case 'STATUS_CHANGED':
        return { title: 'Status changed', detail: this.change(event) };
      case 'PRIORITY_CHANGED':
        return { title: 'Priority changed', detail: this.change(event) };
      case 'COMMENT_ADDED':
        return { title: 'Comment added', detail: null };
      case 'RESOLVED':
        return { title: 'Ticket resolved', detail: event.newValue };
      case 'CLOSED':
        return { title: 'Ticket closed', detail: null };
    }
  }

  private change(event: TicketEvent): string | null {
    return event.oldValue && event.newValue
      ? `${this.label(event.oldValue)} → ${this.label(event.newValue)}`
      : null;
  }
}