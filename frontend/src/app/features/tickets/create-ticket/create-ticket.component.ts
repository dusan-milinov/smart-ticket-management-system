import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { TicketService} from '../services/ticket.service';
import type { TicketPriority, TicketType } from '../models/create-ticket-request.model';

@Component({
  selector: 'app-create-ticket',
  imports: [ReactiveFormsModule, RouterLink, PageHeaderComponent],
  templateUrl: './create-ticket.component.html',
  styleUrl: './create-ticket.component.css',
})
export class CreateTicketComponent {
  private readonly tickets = inject(TicketService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly createdTicketNumber = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.pattern(/\S/)]],
    description: ['', [Validators.required, Validators.pattern(/\S/)]],
    ticketPriority: this.fb.nonNullable.control<TicketPriority | ''>('', Validators.required),
    ticketType: this.fb.nonNullable.control<TicketType | ''>('', Validators.required),
  });

  onSubmit() {
    if (this.submitting()) {
      return;
    }
    this.submitError.set(null);
    this.createdTicketNumber.set(null);
    this.form.markAllAsTouched();
    const { title, description, ticketPriority, ticketType } = this.form.getRawValue();
    if (this.form.invalid || ticketPriority === '' || ticketType === '') {
      return;
    }

    this.submitting.set(true);
    this.form.disable();
    this.tickets.createTicket({
      title: title.trim(),
      description: description.trim(),
      ticketPriority,
      ticketType,
    }).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.submitting.set(false);
        this.form.enable();
      }),
    ).subscribe({
      next: (ticket) => {
        this.createdTicketNumber.set(ticket.ticketNumber);
        this.form.reset();
      },
      error: (error: unknown) => {
        console.error('Could not create ticket', error);
        let message = 'Could not create your ticket. Please try again.';
        if (error instanceof HttpErrorResponse) {
          const body: unknown = error.error;
          if (error.status === 401) {
            message = 'Your session has expired. Please sign in again before submitting your ticket.';
          } else if (error.status === 403) {
            message = 'You do not have permission to create tickets.';
          } else if (body && typeof body === 'object' && 'message' in body
            && typeof body.message === 'string' && body.message.trim()) {
            message = body.message;
          }
        }
        this.submitError.set(message);
      },
    });
  }
}
