import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal, WritableSignal } from '@angular/core';
import { AuthService, CurrentUser } from '../../core/auth.service';
import { ROLES } from '../../core/roles';
import { AccountDirectoryEntry } from './account-directory-entry.model';
import { UserDirectoryComponent } from './user-directory.component';
import { UserDirectoryService } from './user-directory.service';
import { Page } from '../../shared/models/page.model';
import { Observable, of, throwError } from 'rxjs';
import { provideRouter } from '@angular/router';

describe('UserDirectoryComponent', () => {
  let fixture: ComponentFixture<UserDirectoryComponent>;
  let currentUser: WritableSignal<CurrentUser | null>;
  let requests: string[];
  let deactivateResult: Observable<AccountDirectoryEntry>;

  const usersPage: Page<AccountDirectoryEntry> = {
    content: [{ id: 1, email: 'user@example.com', role: 'USER', active: true }],
    number: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  };

  beforeEach(async () => {
    requests = [];
    deactivateResult = of({ id: 1, email: 'user@example.com', role: 'USER', active: false });
    currentUser = signal<CurrentUser | null>({ email: 'admin@example.com', role: ROLES.ADMIN });
    const service = {
      getNonAdminUsers: () => {
        requests.push('non-admin-users');
        return of(usersPage);
      },
      getAllUsers: () => {
        requests.push('all-users');
        return of(usersPage);
      },
      deactivateUser: (id: number) => {
        requests.push('deactivate-' + id);
        return deactivateResult;
      },
      getSupportAgents: () => {
        requests.push('support-agents');
        return of({
          ...usersPage,
          content: [{ id: 2, email: 'agent@example.com', role: 'SUPPORT_AGENT', active: false }],
        });
      },
    };

    await TestBed.configureTestingModule({
      imports: [UserDirectoryComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: currentUser.asReadonly() } },
        { provide: UserDirectoryService, useValue: service },
      ],
    }).compileComponents();
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(UserDirectoryComponent);
    fixture.detectChanges();
  }

  it('shows administrators the all-users and support-agent directories', () => {
    createComponent();
    expect(requests).toEqual(['all-users']);
    expect(fixture.nativeElement.textContent).toContain('user@example.com');

    const agentTab = Array.from(
      fixture.nativeElement.querySelectorAll('[role="tab"]') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Support Agents'));
    agentTab?.click();
    fixture.detectChanges();

    expect(requests).toEqual(['all-users', 'support-agents']);
    expect(fixture.nativeElement.textContent).toContain('agent@example.com');
    expect(fixture.nativeElement.textContent).toContain('Inactive');
  });

  it('shows support agents the non-admin user list without admin-only controls', () => {
    currentUser.set({ email: 'agent@example.com', role: ROLES.SUPPORT_AGENT });
    createComponent();

    expect(requests).toEqual(['non-admin-users']);
    expect(fixture.nativeElement.querySelector('[role="tablist"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('user@example.com');
    expect(fixture.nativeElement.textContent).toContain('Active');
    expect(fixture.nativeElement.querySelector('.badge-active')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.btn-danger')).toBeNull();
  });

  function click(text: string): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((candidate) => candidate.textContent?.trim() === text);
    button?.click();
    fixture.detectChanges();
  }

  function confirmDeactivation(): void {
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('.dialog button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.trim() === 'Deactivate');
    button?.click();
    fixture.detectChanges();
  }

  it('deactivates an active user after confirmation and marks it inactive', () => {
    createComponent();

    click('Deactivate');
    expect(requests).not.toContain('deactivate-1');
    confirmDeactivation();

    expect(requests).toContain('deactivate-1');
    expect(fixture.nativeElement.querySelector('.badge-inactive')).not.toBeNull();
  });

  it('displays the server message when deactivation is rejected', () => {
    deactivateResult = throwError(
      () => new HttpErrorResponse({ status: 409, error: { message: 'Has unresolved tickets' } }),
    );
    createComponent();

    click('Deactivate');
    confirmDeactivation();

    expect(fixture.nativeElement.textContent).toContain('Has unresolved tickets');
    expect(fixture.nativeElement.querySelector('.badge-active')).not.toBeNull();
  });
});
