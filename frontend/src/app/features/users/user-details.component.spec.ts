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

  let ticketPage: Page<Ticket>;

  beforeEach(() => {
    ticketPage = {
      content: [{
        ticketId: 1,
        ticketNumber: 'T-1',
        title: 'Broken printer',
        description: 'Printer is offline',
        ticketStatus: 'IN_QUEUE',
        ticketPriority: 'HIGH',
        resolutionSummary: null,
        createdAt: '2026-10-26T10:00:00Z',
        updatedAt: '2026-10-26T10:00:00Z',
        creatorId: 2,
        creatorEmail: 'user@example.com',
        assigneeId: null,
        assigneeEmail: null,
        ticketType: 'INCIDENT',
      }],
      number: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
    };
  });

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
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

    const root = fixture.nativeElement as HTMLElement;
    const text = root.textContent;
    expect(text).toContain('user@example.com');
    expect(text).toContain('Broken printer');
    expect(root.querySelector('.status-in_queue')?.textContent?.trim()).toBe('Pending');
    expect(root.querySelector('.priority-high')?.textContent?.trim()).toBe('High');
    expect(text).toContain('Submitted');
    expect(root.querySelector<HTMLAnchorElement>('td[data-label="Actions"] a')?.getAttribute('href'))
      .toBe('/tickets/1?returnTo=%2Fusers%2F2');
    expect(root.querySelector('td[data-label="Submitted"]')?.textContent).toMatch(/Oct 26, 2026/);
    expect(root.querySelectorAll('thead th')).toHaveLength(7);
    expect(root.querySelectorAll('tbody tr:first-child td')).toHaveLength(7);
    expect(root.querySelector('td[data-label="Actions"] button')?.textContent?.trim()).toBe('Reassign');
    expect(fixture.nativeElement.querySelector('.badge-active')).not.toBeNull();
  });

  it.each(['IN_QUEUE', 'FULFILLMENT', 'RESOLVED', 'CANCELLED'])(
    'keeps View available for %s tickets and only offers reassignment for open tickets',
    (status) => {
      ticketPage.content[0].ticketStatus = status;
      setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

      const actions = (fixture.nativeElement as HTMLElement).querySelector('td[data-label="Actions"]')!;
      expect(actions.querySelector('a')?.getAttribute('href')).toBe('/tickets/1?returnTo=%2Fusers%2F2');
      expect(actions.querySelector('button') !== null).toBe(['IN_QUEUE', 'FULFILLMENT'].includes(status));
    },
  );

  it('spans all ticket columns when there are no related tickets', () => {
    ticketPage = { ...ticketPage, content: [], totalElements: 0, totalPages: 0, empty: true };
    setup(ROLES.ADMIN, { id: 2, email: 'user@example.com', role: 'USER', active: true });

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.empty')?.getAttribute('colspan')).toBe(String(root.querySelectorAll('thead th').length));
    expect(root.querySelector('.empty')?.textContent).toContain('No tickets found.');
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
