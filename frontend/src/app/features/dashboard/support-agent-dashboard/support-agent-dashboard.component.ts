import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/stat-card/stat-card.component';
import { Ticket } from '../../tickets/models/ticket.model';
import { TicketService, type TicketStatus } from '../../tickets/services/ticket.service';

const DONUT_CIRCUMFERENCE = 2 * Math.PI * 16;

@Component({
  selector: 'app-support-agent-dashboard',
  imports: [PageHeaderComponent, RouterLink, StatCardComponent],
  templateUrl: './support-agent-dashboard.component.html',
  styleUrls: ['./support-agent-dashboard.component.css']
})
export class SupportAgentDashboardComponent implements OnInit {
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private countsRequest?: Subscription;
  private queueRequest?: Subscription;

  readonly counts = signal<Partial<Record<TicketStatus, number>> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly queueLoading = signal(true);
  readonly queueError = signal<string | null>(null);
  readonly priorityQueue = signal<Ticket[]>([]);

  readonly assignedToMe = computed(() => {
    const counts = this.counts();
    return counts
      ? `${Object.values(counts).reduce((total, count) => total + (count ?? 0), 0)}`
      : '—';
  });
  readonly inProgress = computed(() => this.statusCount('FULFILLMENT'));
  readonly pending = computed(() => this.statusCount('IN_QUEUE'));
  readonly resolved = computed(() => this.statusCount('RESOLVED'));
  readonly cancelled = computed(() => this.statusCount('CANCELLED'));
  readonly statusSegments = computed(() => {
    const counts = this.counts() ?? {};
    const statuses = [
      { status: 'FULFILLMENT', label: 'In Progress', color: '#3b82f6' },
      { status: 'IN_QUEUE', label: 'Pending', color: '#f0ad4e' },
      { status: 'RESOLVED', label: 'Resolved', color: '#35a779' },
      { status: 'CANCELLED', label: 'Cancelled', color: '#9aa5b1' },
    ] as const;
    const total = statuses.reduce((sum, item) => sum + (counts[item.status] ?? 0), 0);
    let offset = 0;

    return statuses.map((item) => {
      const count = counts[item.status] ?? 0;
      const length = total > 0 ? (count / total) * DONUT_CIRCUMFERENCE : 0;
      const segment = {
        ...item,
        count,
        dashArray: `${length} ${DONUT_CIRCUMFERENCE - length}`,
        dashOffset: -offset,
      };
      offset += length;
      return segment;
    });
  });

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.countsRequest?.unsubscribe();
      this.queueRequest?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.loadCounts();
    this.loadPriorityQueue();
  }

  loadCounts(): void {
    this.loading.set(true);
    this.error.set(null);
    this.counts.set(null);
    this.countsRequest?.unsubscribe();
    this.countsRequest = this.ticketService.getAssignedTicketStatusCounts().subscribe({
      next: (counts) => {
        this.counts.set(counts);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load ticket status counts.'));
        this.loading.set(false);
      },
    });
  }

  loadPriorityQueue(): void {
    this.queueLoading.set(true);
    this.queueError.set(null);
    this.priorityQueue.set([]);
    this.queueRequest?.unsubscribe();
    this.queueRequest = this.ticketService.getAssignedPriorityQueue().subscribe({
      next: (tickets) => {
        this.priorityQueue.set(tickets);
        this.queueLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.queueError.set(err.status === 0 ? 'Cannot reach the server.' : (err.error?.message ?? 'Could not load the priority queue.'));
        this.queueLoading.set(false);
      },
    });
  }

  private statusCount(status: TicketStatus): string {
    const counts = this.counts();
    if (!counts) return '—';

    return `${counts[status] ?? 0}`;
  }
}
