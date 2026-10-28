import { Component, computed, DestroyRef, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { isRole, ROLE_LABELS, ROLES } from '../../core/roles';
import { TicketService } from '../tickets/services/ticket.service';
import { TicketStatus } from '../tickets/ticket-presentation';
import { ResolvedTicketDay } from '../tickets/models/resolved-ticket-day.model';
import { Subscription } from 'rxjs';

export const MIN_PASSWORD_LENGTH = 8;

const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('newPassword')?.value === control.get('confirmPassword')?.value ? null : { passwordMismatch: true };

@Component({
  selector: 'app-profile',
  imports: [PageHeaderComponent, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly ticketService = inject(TicketService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly passwordDialog = viewChild.required<ElementRef<HTMLDialogElement>>('passwordDialog');
  private readonly passwordTrigger = viewChild.required<ElementRef<HTMLButtonElement>>('passwordTrigger');

  protected readonly user = this.authService.user;
  protected readonly initials = computed(() => {
    const prefix = this.user()?.email?.split('@')[0] ?? '';
    return prefix.split(/[._+\-\s]+/).filter(Boolean).map((part) => Array.from(part)[0]).join('').toUpperCase() || '?';
  });
  protected readonly roleLabel = computed(() => {
    const role = this.user()?.role;
    return isRole(role) ? ROLE_LABELS[role] : 'Unknown role';
  });
  protected readonly activityLoading = signal(false);
  protected readonly activityError = signal<string | null>(null);
  protected readonly statusCounts = signal<Partial<Record<TicketStatus, number>> | null>(null);
  protected readonly statistics = computed(() => {
    const counts = this.statusCounts();
    return [
      { label: 'Total Tickets', value: counts ? Object.values(counts).reduce((sum, count) => sum + count, 0) : null, tone: 'total' },
      { label: 'Open Tickets', value: counts ? (counts.IN_QUEUE ?? 0) + (counts.FULFILLMENT ?? 0) : null, tone: 'open' },
      { label: 'In Progress', value: counts ? counts.FULFILLMENT ?? 0 : null, tone: 'progress' },
      { label: 'Resolved', value: counts ? counts.RESOLVED ?? 0 : null, tone: 'resolved' },
    ];
  });
  protected readonly minimumPasswordLength = MIN_PASSWORD_LENGTH;
  private performanceRequest?: Subscription;
  protected readonly isSupportAgent = computed(() => this.user()?.role === ROLES.SUPPORT_AGENT);
  protected readonly performanceDays = signal<7 | 30>(7);
  protected readonly displayedPerformanceDays = signal<7 | 30>(7);
  protected readonly performanceLoading = signal(false);
  protected readonly performanceError = signal<string | null>(null);
  protected readonly dailyResolutions = signal<ResolvedTicketDay[]>([]);
  protected readonly resolvedTotal = computed(() => this.dailyResolutions().reduce((sum, day) => sum + day.count, 0));
  protected readonly performanceChart = computed(() => {
    const days = this.dailyResolutions();
    const maximum = Math.max(4, ...days.map((day) => day.count));
    const ceiling = Math.ceil(maximum / 4) * 4;
    const points = days.map((day, index) => ({
      ...day,
      x: 48 + index * 632 / Math.max(1, days.length - 1),
      y: 210 - day.count / ceiling * 180,
      label: new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', timeZone: 'UTC' })
        .format(new Date(`${day.date}T00:00:00Z`)),
    }));
    const line = points.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.y}`).join(' ');
    return {
      points,
      line,
      area: points.length ? `${line} L 680 210 L 48 210 Z` : '',
      ticks: Array.from({ length: 5 }, (_, index) => ({ value: ceiling * index / 4, y: 210 - index * 45 })),
      dates: points.filter((_, index) => index === 0 || index === Math.floor((days.length - 1) / 2) || index === days.length - 1),
    };
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly passwordForm = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.performanceRequest?.unsubscribe());
    if (this.isSupportAgent()) this.loadPerformance(7);
  }

  protected loadPerformance(days: 7 | 30): void {
    if (days === this.performanceDays() && !this.performanceError() &&
        (this.performanceLoading() || this.dailyResolutions().length > 0)) return;
    this.performanceRequest?.unsubscribe();
    this.performanceDays.set(days);
    this.performanceLoading.set(true);
    this.performanceError.set(null);
    this.performanceRequest = this.ticketService.getMyDailyResolutions(days).subscribe({
      next: (data) => {
        this.dailyResolutions.set(data);
        this.displayedPerformanceDays.set(days);
        this.performanceLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.performanceError.set(error.status === 0 ? 'Cannot reach the server.' : (error.error?.message ?? 'Could not load performance data.'));
        this.performanceLoading.set(false);
      },
    });
  }

  protected openPasswordDialog(): void {
    this.passwordForm.reset();
    this.error.set(null);
    this.passwordDialog().nativeElement.showModal();
  }

  protected closePasswordDialog(): void {
    if (this.submitting()) return;
    this.passwordDialog().nativeElement.close();
    this.resetPasswordDialog();
  }

  protected cancelPasswordDialog(event: Event): void {
    event.preventDefault();
    this.closePasswordDialog();
  }

  protected resetPasswordDialog(): void {
    this.passwordForm.reset();
    this.error.set(null);
    this.passwordTrigger().nativeElement.focus();
  }

  protected submit(): void {
    if (this.passwordForm.invalid || this.submitting()) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.error.set(null);
    this.submitting.set(true);
    this.authService.changePassword(currentPassword, newPassword).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closePasswordDialog();
        void this.router.navigate(['/login']);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(
          error.status === 0 ? 'Cannot reach the server.' : (error.error?.message ?? 'Could not change the password.'),
        );
        this.submitting.set(false);
      },
    });
  }
}
