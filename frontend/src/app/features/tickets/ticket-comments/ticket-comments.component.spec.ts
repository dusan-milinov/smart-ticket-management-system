import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AuthService, CurrentUser } from '../../../core/auth.service';
import { ROLES } from '../../../core/roles';
import { TicketComment } from '../models/ticket-comment.model';
import { TicketCommentsComponent } from './ticket-comments.component';

describe('TicketCommentsComponent', () => {
  let fixture: ComponentFixture<TicketCommentsComponent>;
  let http: HttpTestingController;
  let user: ReturnType<typeof signal<CurrentUser | null>>;
  let element: HTMLElement;

  const comment = (
    id: number,
    authorRole: TicketComment['authorRole'] = 'USER',
    createdAt = '2026-10-27T12:00:00Z',
  ): TicketComment => ({
    id, authorRole, createdAt, authorId: 7, authorEmail: `${authorRole.toLowerCase()}@example.com`,
    ticketId: 42, content: `Comment ${id}`,
  });
  const render = () => fixture.detectChanges();
  const load = (comments: TicketComment[] = []) => {
    http.expectOne('/api/comments/42').flush(comments);
    render();
  };
  const write = (value: string) => {
    const textarea = element.querySelector('textarea')!;
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
    render();
  };
  const submit = () => {
    element.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    render();
  };
  const postButton = () => element.querySelector<HTMLButtonElement>('button[type="submit"]')!;

  beforeEach(() => {
    user = signal<CurrentUser | null>({ email: 'user@example.com', role: ROLES.USER });
    TestBed.configureTestingModule({
      imports: [TicketCommentsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user: user.asReadonly() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketCommentsComponent);
    fixture.componentRef.setInput('ticketId', 42);
    element = fixture.nativeElement;
    render();
  });

  afterEach(() => http.verify());

  it('shows loading, an empty state and the count, expanded by default', () => {
    expect(element.textContent).toContain('Loading comments...');
    load();
    expect(element.textContent).toContain('No comments yet.');
    expect(element.querySelector('.count')?.textContent).toBe('0');
    expect(element.querySelector('.comments-header')?.getAttribute('aria-expanded')).toBe('true');
  });

  it('sorts oldest first and places comments by the author role from the API', () => {
    load([comment(3, 'SUPPORT_AGENT'), comment(2, 'USER'), comment(1, 'ADMIN', '2026-10-26T12:00:00Z')]);
    expect([...element.querySelectorAll('.content')].map((node) => node.textContent)).toEqual([
      'Comment 1', 'Comment 2', 'Comment 3',
    ]);
    expect([...element.querySelectorAll('.timeline-item')].map((node) => node.className)).toEqual([
      'timeline-item operator', 'timeline-item user', 'timeline-item operator',
    ]);
    expect([...element.querySelectorAll('.role')].map((node) => node.textContent)).toEqual([
      'Admin', 'User', 'Support Agent',
    ]);
    expect(element.textContent).toContain('user@example.com');
    expect(element.querySelector('time')?.getAttribute('datetime')).toBe('2026-10-26T12:00:00Z');
  });

  it('collapses and expands without removing the draft', () => {
    load();
    write('Keep this draft');
    const toggle = element.querySelector<HTMLButtonElement>('.comments-header')!;
    const panel = element.querySelector<HTMLElement>('.collapsible')!;
    toggle.click();
    render();
    expect(panel.classList.contains('open')).toBe(false);
    expect(panel.hasAttribute('inert')).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe(panel.id);
    toggle.click();
    render();
    expect(panel.classList.contains('open')).toBe(true);
    expect(panel.hasAttribute('inert')).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(element.querySelector('textarea')?.value).toBe('Keep this draft');
  });

  it('blocks whitespace and duplicate submissions, posts trimmed multiline text and clears on success', () => {
    load();
    write(' \n  ');
    expect(postButton().disabled).toBe(true);
    submit();
    http.expectNone('/api/comments');
    write('  First line\nSecond line  ');
    submit();
    const request = http.expectOne('/api/comments');
    expect(request.request.body).toEqual({ ticketId: 42, content: 'First line\nSecond line' });
    expect(postButton().disabled).toBe(true);
    expect(element.querySelector('textarea')?.disabled).toBe(true);
    submit();
    http.expectNone('/api/comments');
    request.flush({ ...comment(1), content: 'First line\nSecond line' });
    render();
    expect(element.querySelector('textarea')?.value).toBe('');
    expect(element.querySelector('.count')?.textContent).toBe('1');
    expect(element.querySelector('.timeline-item.user')).not.toBeNull();
    expect(element.textContent).toContain('user@example.com');
    expect(element.textContent).toContain('Comment posted.');
  });

  it.each([
    ['USER', 'user', 'User'],
    ['SUPPORT_AGENT', 'operator', 'Support Agent'],
    ['ADMIN', 'operator', 'Admin'],
  ] as const)('keeps consecutive %s comments on the same side', (role, side, label) => {
    load([comment(1, role)]);
    for (const id of [2, 3]) {
      write(`Comment ${id}`);
      submit();
      http.expectOne('/api/comments').flush(comment(id, role));
      render();
    }
    expect(element.querySelectorAll(`.timeline-item.${side}`)).toHaveLength(3);
    expect([...element.querySelectorAll('.role')].every((node) => node.textContent === label)).toBe(true);
  });

  it('retains the draft and allows retry after a failed post', () => {
    load();
    write('Do not lose this');
    submit();
    http.expectOne('/api/comments').flush({}, { status: 500, statusText: 'Server error' });
    render();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not post your comment');
    expect(element.querySelector('textarea')?.value).toBe('Do not lose this');
    expect(postButton().disabled).toBe(false);
    submit();
    http.expectOne('/api/comments').flush(comment(1));
    render();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('displays load errors and retries without permitting posts against an incomplete timeline', () => {
    http.expectOne('/api/comments/42').flush({}, { status: 403, statusText: 'Forbidden' });
    render();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('permission');
    write('A draft');
    expect(postButton().disabled).toBe(true);
    element.querySelector<HTMLButtonElement>('.secondary')!.click();
    render();
    load();
    expect(postButton().disabled).toBe(false);
  });

  it('requires an authenticated session to post', () => {
    load();
    user.set(null);
    render();
    expect(postButton().disabled).toBe(true);
    expect(element.querySelector('textarea')?.disabled).toBe(true);
    expect(element.textContent).toContain('Sign in to post a comment.');
  });

  it('cancels old requests and resets state when the ticket changes', () => {
    const oldRequest = http.expectOne('/api/comments/42');
    fixture.componentRef.setInput('ticketId', 43);
    render();
    expect(oldRequest.cancelled).toBe(true);
    http.expectOne('/api/comments/43').flush([]);
    render();
    write('Old ticket draft');
    submit();
    const post = http.expectOne('/api/comments');
    fixture.componentRef.setInput('ticketId', 44);
    render();
    expect(post.cancelled).toBe(true);
    http.expectOne('/api/comments/44').flush([]);
    render();
    expect(element.querySelector('textarea')?.value).toBe('');
    expect(element.querySelector('.count')?.textContent).toBe('0');
  });

  it('cancels a pending retry when the component is destroyed', () => {
    http.expectOne('/api/comments/42').flush({}, { status: 500, statusText: 'Server error' });
    render();
    element.querySelector<HTMLButtonElement>('.secondary')!.click();
    const retry = http.expectOne('/api/comments/42');
    fixture.destroy();
    expect(retry.cancelled).toBe(true);
  });
});
