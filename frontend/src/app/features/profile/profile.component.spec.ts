import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService, CurrentUser } from '../../core/auth.service';
import { ROLES } from '../../core/roles';
import { TicketService } from '../tickets/services/ticket.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  const user = signal<CurrentUser | null>(null);
  const changePassword = vi.fn();
  const getMyTicketStatusCounts = vi.fn();
  const getAssignedTicketStatusCounts = vi.fn();
  const counts = { IN_QUEUE: 3, FULFILLMENT: 2, RESOLVED: 7, CANCELLED: 1 };

  function element<T extends Element>(selector: string): T {
    const result = fixture.nativeElement.querySelector(selector);
    if (!result) throw new Error(`Missing element: ${selector}`);
    return result;
  }

  function openDialog(): HTMLDialogElement {
    element<HTMLButtonElement>('.security-card button').click();
    fixture.detectChanges();
    return element<HTMLDialogElement>('dialog');
  }

  function fillPasswords(currentPassword = 'current-password', newPassword = 'new-password', confirmPassword = newPassword): void {
    for (const [id, value] of Object.entries({ 'current-password': currentPassword, 'new-password': newPassword, 'confirm-password': confirmPassword })) {
      const input = element<HTMLInputElement>(`#${id}`);
      input.value = value;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
    }
    fixture.detectChanges();
  }

  function submit(): void {
    element<HTMLFormElement>('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    changePassword.mockReset();
    getMyTicketStatusCounts.mockReset().mockReturnValue(of(counts));
    getAssignedTicketStatusCounts.mockReset().mockReturnValue(of(counts));
    user.set({ email: 'user@example.com', role: ROLES.USER });
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: user.asReadonly(), changePassword } },
        { provide: TicketService, useValue: { getMyTicketStatusCounts, getAssignedTicketStatusCounts } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    const dialog = element<HTMLDialogElement>('dialog');
    // jsdom does not implement native modal methods or focus containment.
    Object.defineProperties(dialog, {
      showModal: { value: () => {
        dialog.open = true;
        element<HTMLInputElement>('#current-password').focus();
      } },
      close: { value: () => {
        dialog.open = false;
        dialog.dispatchEvent(new Event('close'));
      } },
    });
  });

  it.each([
    ['ivan.petrovic@gmail.com', 'IP'],
    ['marija@gmail.com', 'M'],
    ['..ivan..petrovic.@gmail.com', 'IP'],
    ['ana-marija_test+support@gmail.com', 'AMTS'],
    ['', '?'],
    ['@gmail.com', '?'],
  ])('generates initials for %s', (email, initials) => {
    user.set({ email, role: ROLES.USER });
    fixture.detectChanges();
    expect(element('.avatar').textContent?.trim()).toBe(initials);
  });

  it('handles a missing current user without showing activity', () => {
    user.set(null);
    fixture.detectChanges();
    expect(element('.avatar').textContent?.trim()).toBe('?');
    expect(fixture.nativeElement.textContent).toContain('Email unavailable');
    expect(fixture.nativeElement.querySelector('.activity')).toBeNull();
  });

  it('uses status counts for totals including cancelled tickets and the existing definition of open', () => {
    const values = Array.from(fixture.nativeElement.querySelectorAll('.stat-card strong'), (node) => (node as HTMLElement).textContent?.trim());
    expect(values).toEqual(['13', '5', '2', '7']);
    expect(getMyTicketStatusCounts).toHaveBeenCalledOnce();
    expect(getAssignedTicketStatusCounts).not.toHaveBeenCalled();
  });

  it('does not show activity or request ticket counts for an operator', () => {
    fixture.destroy();
    getMyTicketStatusCounts.mockClear();
    user.set({ email: 'agent@example.com', role: ROLES.SUPPORT_AGENT });
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    expect(getAssignedTicketStatusCounts).not.toHaveBeenCalled();
    expect(getMyTicketStatusCounts).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.activity')).toBeNull();
  });

  it('does not request activity for an administrator', () => {
    fixture.destroy();
    getMyTicketStatusCounts.mockClear();
    user.set({ email: 'admin@example.com', role: ROLES.ADMIN });
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    expect(getMyTicketStatusCounts).not.toHaveBeenCalled();
    expect(getAssignedTicketStatusCounts).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.activity')).toBeNull();
  });

  it('shows loading, failure and retry without presenting fake zero counts', () => {
    fixture.destroy();
    const request = new Subject<typeof counts>();
    getMyTicketStatusCounts.mockReturnValueOnce(request);
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading ticket activity');
    expect(element('.stat-card strong').textContent).toContain('—');
    request.error({ status: 0 });
    fixture.detectChanges();
    expect(element('[role="alert"]').textContent).toContain('Cannot reach the server.');
    expect(fixture.nativeElement.querySelector('.stat-card')).toBeNull();
    element<HTMLButtonElement>('.activity-error button').click();
    fixture.detectChanges();
    expect(element('.stat-card strong').textContent?.trim()).toBe('13');
  });

  it('treats omitted status counts as zero after a successful response', () => {
    fixture.destroy();
    getMyTicketStatusCounts.mockReturnValue(of({}));
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    expect(Array.from(fixture.nativeElement.querySelectorAll('.stat-card strong'), (node) => (node as HTMLElement).textContent?.trim())).toEqual(['0', '0', '0', '0']);
  });

  it.each(['cancel', 'keydown'])('opens on demand and resets values and restores focus on Escape (%s)', (eventType) => {
    expect(element<HTMLDialogElement>('dialog').open).toBe(false);
    const dialog = openDialog();
    expect(dialog.open).toBe(true);
    expect(document.activeElement).toBe(element('#current-password'));
    fillPasswords();
    const cancel = eventType === 'cancel'
      ? new Event('cancel', { cancelable: true })
      : new KeyboardEvent('keydown', { key: 'Escape', cancelable: true, bubbles: true });
    dialog.dispatchEvent(cancel);
    fixture.detectChanges();
    expect(cancel.defaultPrevented).toBe(true);
    expect(dialog.open).toBe(false);
    expect(element<HTMLInputElement>('#current-password').value).toBe('');
    expect(element<HTMLInputElement>('#new-password').value).toBe('');
    expect(element<HTMLInputElement>('#confirm-password').value).toBe('');
    expect(document.activeElement).toBe(element('.security-card button'));
  });

  it.each(['.close-button', '.dialog-actions button[type="button"]'])('closes via %s and clears errors before reopening', (selector) => {
    changePassword.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Current password is incorrect' } })));
    openDialog();
    fillPasswords();
    submit();
    expect(element('.form-error').textContent).toContain('Current password is incorrect');
    element<HTMLButtonElement>(selector).click();
    fixture.detectChanges();
    openDialog();
    expect(fixture.nativeElement.querySelector('.form-error')).toBeNull();
    expect(element<HTMLInputElement>('#current-password').value).toBe('');
  });

  it('validates required fields, minimum length and confirmation without sending a request', () => {
    openDialog();
    submit();
    expect(element('#current-password-error').textContent).toContain('Enter your current password.');
    expect(element('#confirm-password-error').textContent).toContain('Confirm your new password.');
    fillPasswords('current-password', 'short', 'different');
    submit();
    expect(element('#new-password-error').textContent).toContain('at least 8 characters');
    expect(element('#confirm-password-error').textContent).toContain('Passwords do not match.');
    expect(changePassword).not.toHaveBeenCalled();
  });

  it('prevents duplicate submissions and dismissal while saving, then returns to login', () => {
    const request = new Subject<void>();
    changePassword.mockReturnValue(request);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const dialog = openDialog();
    fillPasswords();
    submit();
    submit();
    dialog.dispatchEvent(new Event('cancel', { cancelable: true }));
    expect(dialog.open).toBe(true);
    expect(element<HTMLButtonElement>('button[type="submit"]').disabled).toBe(true);
    expect(element<HTMLFieldSetElement>('fieldset').disabled).toBe(true);
    expect(changePassword).toHaveBeenCalledExactlyOnceWith('current-password', 'new-password');
    request.next();
    request.complete();
    fixture.detectChanges();
    expect(dialog.open).toBe(false);
    expect(element<HTMLInputElement>('#new-password').value).toBe('');
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('shows submission errors inline and permits retry', () => {
    changePassword.mockReturnValue(throwError(() => ({ status: 0 })));
    openDialog();
    fillPasswords();
    submit();
    expect(element('.form-error').textContent).toContain('Cannot reach the server.');
    expect(element<HTMLButtonElement>('button[type="submit"]').disabled).toBe(false);
    expect(element<HTMLInputElement>('#new-password').value).toBe('new-password');
  });
});
