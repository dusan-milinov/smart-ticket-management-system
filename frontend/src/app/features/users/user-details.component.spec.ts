import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ROLES } from '../../core/roles';
import { Page } from '../../shared/models/page.model';
import { Ticket } from '../tickets/models/ticket.model';
import { AccountDirectoryEntry } from './account-directory-entry.model';
import { TicketService } from '../tickets/services/ticket.service';
import { UserDetailsComponent } from './user-details.component';
import { UserDirectoryService } from './user-directory.service';

describe('UserDetailsComponent', () => {
  let fixture: ComponentFixture<UserDetailsComponent>;
  let deactivations: number[];
  let reassignments: [number, number][];
  let reassignResult: Observable<Ticket>;
  let deactivateResult: Observable<AccountDirectoryEntry>;

  const ticketPage = {
    content: [
      {
        ticketId: 1, ticketNumber: 'T-1', title: 'Broken printer', ticketStatus: 'IN_QUEUE',
        ticketPriority: 'HIGH', ticketType: 'INCIDENT',
      },
    ],
    number: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
  } as unknown as Page<Ticket>;

  function setup(role: string, account: AccountDirectoryEntry): void {
    deactivations = [];
    reassignments = [];
    reassignResult = of({} as Ticket);
    deactivateResult = of({ ...account, active: false });
    TestBed.configureTestingModule({
      imports: [UserDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: String(account.id) }) } } },
        { provide: AuthService, useValue: { user: signal({ email: 'x@example.com', role }).asReadonly() } },
        {
          provide: TicketService,
          useValue: {
            reassignTicket: (ticketId: number, assigneeId: number) => {
              reassignments.push([ticketId, assigneeId]);
              return reassignResult;
            },
          },
        },
        {
          provide: UserDirectoryService,
          useValue: {
            getUser: () => of(account),
            getUserTickets: () => of(ticketPage),
            getSupportAgents: () =>
              of({ content: [{ id: 9, email: 'agent9@example.com', role: 'SUPPORT_AGENT', active: true }] }),
            deactivateUser: (id: number) => {
              deactivations.push(id);
              return deactivateResult;
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(UserDetailsComponent);
    fixture.detectChanges();
  }

  function click(text: string): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((b) => b.textContent?.trim() === text);
    button?.click();
    fixture.detectChanges();
  }

  it('shows account details and ticket submission time with a View action', () => {
    ticketPage.content[0] = {
      ...ticketPage.content[0],
      createdAt: '2026-10-26T10:00:00Z',
    } as Ticket;
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

    const root = fixture.nativeElement as HTMLElement;
    const text = root.textContent;
    expect(text).toContain('user@example.com');
    expect(text).toContain('Broken printer');
    expect(root.querySelector('.status-in_queue')?.textContent?.trim()).toBe('Pending');
    expect(root.querySelector('.priority-high')?.textContent?.trim()).toBe('High');
    expect(text).toContain('Submitted');
    expect(root.querySelector<HTMLAnchorElement>('td[data-label="Actions"] a')?.getAttribute('href'))
      .toBe('/tickets/1');
    expect(fixture.nativeElement.querySelector('.badge-active')).not.toBeNull();
  });

  it('lets administrators reassign an open ticket through a confirmation popup', () => {
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

    click('Reassign');
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    expect(dialog.textContent).toContain('T-1');
    const confirm = Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Reassign')!;
    expect(confirm.disabled).toBe(true);

    const select = dialog.querySelector('select') as HTMLSelectElement;
    select.value = '9';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(confirm.disabled).toBe(false);
    confirm.click();
    fixture.detectChanges();

    expect(reassignments).toEqual([[1, 9]]);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('T-1 reassigned to agent9@example.com.');
  });

  it('passes filters and sorting to account-related ticket requests', () => {
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });
    const request = vi.spyOn(TestBed.inject(UserDirectoryService), 'getUserTickets');
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.tab:nth-child(2)')!.click();
    const priority = root.querySelector<HTMLSelectElement>('select[aria-label="Filter by priority"]')!;
    priority.value = 'HIGH';
    priority.dispatchEvent(new Event('change'));
    const sort = root.querySelector<HTMLSelectElement>('select[aria-label="Sort tickets"]')!;
    sort.value = 'oldest';
    sort.dispatchEvent(new Event('change'));
    expect(request).toHaveBeenLastCalledWith(2, 0, 10, { status: 'IN_QUEUE', priority: 'HIGH', sort: 'oldest' });
  });

  it('keeps the popup open and shows the server message when reassignment fails', () => {
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });
    reassignResult = throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Agent is inactive' } }));

    click('Reassign');
    const select = fixture.nativeElement.querySelector('[role="dialog"] select') as HTMLSelectElement;
    select.value = '9';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    Array.from(fixture.nativeElement.querySelectorAll('[role="dialog"] button') as NodeListOf<HTMLButtonElement>)
      .find((b) => b.textContent?.trim() === 'Reassign')!
      .click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]').textContent).toContain('Agent is inactive');
  });

  it('closes the reassign popup on cancel without calling the server', () => {
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

    click('Reassign');
    click('Cancel');

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(reassignments).toEqual([]);
  });

  it('does not offer reassignment to support agents', () => {
    setup(ROLES.SUPPORT_AGENT, { id: 2, email: 'user@example.com', role: 'USER', active: true });
    expect(fixture.nativeElement.textContent).not.toContain('Reassign');
  });

  it('hides the deactivate button from support agents', () => {
    setup(ROLES.SUPPORT_AGENT, { id: 2, email: 'user@example.com', role: 'USER', active: true });
    expect(fixture.nativeElement.textContent).not.toContain('Deactivate user');
  });

  it('lets administrators deactivate an active non-admin after confirmation', () => {
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

    click('Deactivate user');
    expect(deactivations).toEqual([]);
    click('Deactivate');

    expect(deactivations).toEqual([2]);
    expect(fixture.nativeElement.querySelector('.badge-inactive')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Deactivate user');
  });

  it('does not offer deactivation for inactive or administrator accounts', () => {
    setup(ROLES.ADMIN, { id: 1, email: 'admin@example.com', role: 'ADMIN', active: true });
    expect(fixture.nativeElement.textContent).not.toContain('Deactivate user');
  });

  it('shows the server message when deactivation fails', () => {
    setup(ROLES.ADMIN, { id: 3, email: 'agent@example.com', role: 'SUPPORT_AGENT', active: true });
    deactivateResult = throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Has unresolved tickets' } }));

    click('Deactivate user');
    click('Deactivate');

    expect(fixture.nativeElement.textContent).toContain('Has unresolved tickets');
  });
});
