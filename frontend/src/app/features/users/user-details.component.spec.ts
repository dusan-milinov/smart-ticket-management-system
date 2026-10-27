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
import { UserDetailsComponent } from './user-details.component';
import { UserDirectoryService } from './user-directory.service';

describe('UserDetailsComponent', () => {
  let fixture: ComponentFixture<UserDetailsComponent>;
  let deactivations: number[];
  let deactivateResult: Observable<AccountDirectoryEntry>;

  const ticketPage = {
    content: [
      { ticketId: 1, ticketNumber: 'T-1', title: 'Broken printer', ticketStatus: 'IN_QUEUE' },
    ],
    number: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
  } as unknown as Page<Ticket>;

  function setup(role: string, account: AccountDirectoryEntry): void {
    deactivations = [];
    deactivateResult = of({ ...account, active: false });
    TestBed.configureTestingModule({
      imports: [UserDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: String(account.id) }) } } },
        { provide: AuthService, useValue: { user: signal({ email: 'x@example.com', role }).asReadonly() } },
        {
          provide: UserDirectoryService,
          useValue: {
            getUser: () => of(account),
            getUserTickets: () => of(ticketPage),
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
    expect(text).toContain('In Queue');
    expect(text).toContain('Submitted');
    expect(root.querySelector<HTMLAnchorElement>('td[data-label="Actions"] a')?.getAttribute('href'))
      .toBe('/tickets/1');
    expect(fixture.nativeElement.querySelector('.badge-active')).not.toBeNull();
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
