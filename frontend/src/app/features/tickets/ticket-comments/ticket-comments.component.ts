import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ROLE_LABELS, ROLES } from '../../../core/roles';
import { TicketComment } from '../models/ticket-comment.model';
import { TicketService } from '../services/ticket.service';

@Component({
  selector: 'app-ticket-comments',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './ticket-comments.component.html',
  styleUrls: ['./ticket-comments.component.css', '../ticket-collapsible.css'],
})
export class TicketCommentsComponent {
  readonly ticketId = input.required<number>();
  readonly commentAdded = output<void>();

  private readonly ticketService = inject(TicketService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private postRequest?: Subscription;
  private loadRequest?: Subscription;

  protected readonly comments = signal<TicketComment[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly postError = signal<string | null>(null);
  protected readonly posting = signal(false);
  protected readonly posted = signal(false);
  protected readonly expanded = signal(true);
  protected readonly draft = signal('');
  protected readonly canPost = computed(() =>
    !!this.draft().trim() && !this.posting() && !this.loading() && !this.loadError() && !!this.auth.user(),
  );
  protected readonly signedIn = computed(() => !!this.auth.user());
  protected readonly panelId = computed(() => `ticket-${this.ticketId()}-comments-panel`);
  protected readonly inputId = computed(() => `ticket-${this.ticketId()}-comment-input`);

  protected readonly timeline = computed(() =>
    [...this.comments()]
      .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id - b.id)
      .map((comment) => {
        const role = ROLES[comment.authorRole];
        return {
          ...comment,
          author: comment.authorEmail,
          role: role === ROLES.SUPPORT_AGENT ? 'Operator' : ROLE_LABELS[role],
          side: role === ROLES.USER ? 'user' : 'operator',
        };
      }),
  );

  constructor() {
    effect((onCleanup) => {
      const ticketId = this.ticketId();
      this.postRequest?.unsubscribe();
      this.comments.set([]);
      this.draft.set('');
      this.postError.set(null);
      this.posting.set(false);
      this.posted.set(false);
      this.expanded.set(true);
      this.loadComments(ticketId);
      onCleanup(() => this.loadRequest?.unsubscribe());
    });
  }

  protected retry(): void {
    this.loadComments(this.ticketId());
  }

  protected postComment(): void {
    if (!this.canPost()) return;

    const ticketId = this.ticketId();
    const content = this.draft().trim();
    this.posting.set(true);
    this.postError.set(null);
    this.posted.set(false);
    this.postRequest = this.ticketService.postTicketComment(ticketId, content)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (comment) => {
          this.comments.update((comments) => [...comments.filter((entry) => entry.id !== comment.id), comment]);
          this.draft.set('');
          this.posting.set(false);
          this.posted.set(true);
          this.commentAdded.emit();
        },
        error: (error: HttpErrorResponse) => {
          this.postError.set(this.errorMessage(error, 'Could not post your comment. Please try again.'));
          this.posting.set(false);
        },
      });
  }

  private loadComments(ticketId: number): void {
    this.loadRequest?.unsubscribe();
    this.loading.set(true);
    this.loadError.set(null);
    this.loadRequest = this.ticketService.getTicketComments(ticketId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (comments) => {
          this.comments.set(comments);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.loadError.set(this.errorMessage(error, 'Could not load comments. Please try again.'));
          this.loading.set(false);
        },
      });
  }

  private errorMessage(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 0) return 'Cannot reach the server. Please try again.';
    if (error.status === 401) return 'Your session has expired. Please sign in again.';
    if (error.status === 403) return 'You do not have permission to access comments on this ticket.';
    return typeof error.error?.message === 'string' ? error.error.message : fallback;
  }
}
