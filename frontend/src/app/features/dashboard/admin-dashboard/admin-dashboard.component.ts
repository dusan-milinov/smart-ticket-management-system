import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AdminDashboardService, AdminDashboardSummary } from './admin-dashboard.service';

@Component({
  selector: 'app-admin-dashboard',
  imports: [DatePipe, DecimalPipe, PageHeaderComponent, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
})
export class AdminDashboardComponent implements OnInit {
  private readonly dashboard = inject(AdminDashboardService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly summary = signal<AdminDashboardSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly updatedAt = signal<Date | null>(null);
  protected readonly openTickets = computed(() => {
    const counts = this.summary()?.ticketsByStatus;
    return counts ? counts.IN_QUEUE + counts.FULFILLMENT : 0;
  });
  protected readonly resolutionRate = computed(() => {
    const data = this.summary();
    return data?.totalTickets ? data.ticketsByStatus.RESOLVED / data.totalTickets * 100 : 0;
  });
  protected readonly statuses = [
    { key: 'IN_QUEUE', label: 'In queue', color: '#b45309' },
    { key: 'FULFILLMENT', label: 'In fulfillment', color: '#2563eb' },
    { key: 'RESOLVED', label: 'Resolved', color: '#15803d' },
    { key: 'CANCELLED', label: 'Cancelled', color: '#64748b' },
  ] as const;
  protected readonly priorities = ['HIGH', 'MEDIUM', 'LOW'] as const;

  ngOnInit(): void { this.load(); }

  protected load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.dashboard.getSummary().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.summary.set(data);
        this.updatedAt.set(new Date());
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.summary.set(null);
        this.error.set(error.status === 0 ? 'Cannot reach the server. Please try again.'
          : error.status === 403 ? 'Only administrators can view this dashboard.'
          : 'Could not load the dashboard. Please try again.');
        this.loading.set(false);
      },
    });
  }

  protected percentage(count: number, total: number): number {
    return total > 0 ? count / total * 100 : 0;
  }

  protected label(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
