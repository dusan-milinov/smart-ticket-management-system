import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { UserDirectoryComponent } from './user-directory.component';
import { UserDirectoryService } from './user-directory.service';
import { Page } from '../../shared/models/page.model';
import { AccountDirectoryEntry } from './account-directory-entry.model';

describe('UserDirectoryComponent', () => {
  let fixture: ComponentFixture<UserDirectoryComponent>;
  let requests: string[];
  let deactivateUser: ReturnType<typeof vi.fn<(id: number) => Observable<AccountDirectoryEntry>>>;
  const usersPage: Page<AccountDirectoryEntry> = {
    content: [{ id: 2, email: 'user@example.com', role: 'USER', active: true }],
    number: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
  };

  beforeEach(async () => {
    requests = [];
    deactivateUser = vi.fn().mockReturnValue(of({ ...usersPage.content[0], active: false }));
    await TestBed.configureTestingModule({
      imports: [UserDirectoryComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map() } } },
        {
          provide: UserDirectoryService,
          useValue: {
            deactivateUser: (id: number) => deactivateUser(id),
            getAllUsers: () => {
              requests.push('users');
              return of(usersPage);
            },
            getSupportAgents: () => {
              requests.push('agents');
              return of({ ...usersPage, content: [{ id: 3, email: 'agent@example.com', role: 'SUPPORT_AGENT', active: true }] });
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(UserDirectoryComponent);
    fixture.detectChanges();
  });

  it('uses the admin directory and exposes View actions instead of email links', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(requests).toEqual(['users']);
    expect(root.querySelector('td[data-label="Email"] a')).toBeNull();
    const view = root.querySelector<HTMLAnchorElement>('td[data-label="Actions"] a');
    expect(view?.textContent?.trim()).toBe('View');
    expect(view?.getAttribute('href')).toBe('/users/2');
  });

  it('switches to the agent list and shows the new-agent action', () => {
    const root = fixture.nativeElement as HTMLElement;
    const agentsTab = Array.from(root.querySelectorAll<HTMLButtonElement>('[role="tab"]'))
      .find((button) => button.textContent?.includes('Support Agents'));
    agentsTab?.click();
    fixture.detectChanges();

    expect(requests).toEqual(['users', 'agents']);
    expect(root.textContent).toContain('agent@example.com');
    expect(root.querySelector<HTMLAnchorElement>('a[actions]')?.getAttribute('href'))
      .toBe('/users/new-agent');
  });

  it('requires confirmation and updates the row after deactivation', () => {
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.row-actions .btn-danger')!.click();
    fixture.detectChanges();
    expect(deactivateUser).not.toHaveBeenCalled();

    root.querySelector<HTMLButtonElement>('.dialog .btn-danger')!.click();
    fixture.detectChanges();
    expect(deactivateUser).toHaveBeenCalledWith(2);
    expect(root.querySelector('.badge-inactive')).not.toBeNull();
    expect(root.querySelector('.row-actions .btn-danger')).toBeNull();
    expect(root.querySelector('.dialog')).toBeNull();
  });

  it('cancels without deactivating the account', () => {
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.row-actions .btn-danger')!.click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.dialog button')!.click();
    fixture.detectChanges();
    expect(deactivateUser).not.toHaveBeenCalled();
    expect(root.querySelector('.dialog')).toBeNull();
  });

  it('shows backend rejection and leaves the account active', () => {
    deactivateUser.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { message: 'Has unresolved tickets' },
    })));
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.row-actions .btn-danger')!.click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.dialog .btn-danger')!.click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Has unresolved tickets');
    expect(root.querySelector('.badge-active')).not.toBeNull();
  });

  it('hides deactivation for inactive and administrator accounts', () => {
    const account = usersPage.content[0];
    const original = { ...account };
    try {
      Object.assign(account, { role: 'ADMIN' });
      fixture.destroy();
      fixture = TestBed.createComponent(UserDirectoryComponent);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.row-actions .btn-danger')).toBeNull();
      Object.assign(account, { role: 'USER', active: false });
      fixture.destroy();
      fixture = TestBed.createComponent(UserDirectoryComponent);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.row-actions .btn-danger')).toBeNull();
    } finally {
      Object.assign(account, original);
    }
  });
});
