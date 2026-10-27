import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  const changePassword = vi.fn();

  beforeEach(async () => {
    changePassword.mockReset();
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user: signal({ email: 'user@example.com', role: 'ROLE_USER' }).asReadonly(),
            changePassword,
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
  });

  it('rejects mismatched passwords without sending a request', () => {
    const component = fixture.componentInstance as any;
    component.passwordForm.setValue({ currentPassword: 'current-password', newPassword: 'new-password', confirmPassword: 'different-password' });
    component.submit();

    expect(changePassword).not.toHaveBeenCalled();
    expect(component.passwordForm.hasError('passwordMismatch')).toBe(true);
  });

  it('changes the password and returns to login after success', () => {
    changePassword.mockReturnValue(of(void 0));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const component = fixture.componentInstance as any;
    component.passwordForm.setValue({ currentPassword: 'current-password', newPassword: 'new-password', confirmPassword: 'new-password' });
    component.submit();

    expect(changePassword).toHaveBeenCalledWith('current-password', 'new-password');
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('renders the server error on failure', () => {
    changePassword.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Current password is incorrect' } })));
    const component = fixture.componentInstance as any;
    component.passwordForm.setValue({ currentPassword: 'current-password', newPassword: 'new-password', confirmPassword: 'new-password' });
    component.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Current password is incorrect');
  });
});
