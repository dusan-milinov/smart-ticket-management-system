import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

export const MIN_PASSWORD_LENGTH = 8;

const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('newPassword')?.value === control.get('confirmPassword')?.value ? null : { passwordMismatch: true };

@Component({
  selector: 'app-profile',
  imports: [PageHeaderComponent, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly user = this.authService.user;
  protected readonly minimumPasswordLength = MIN_PASSWORD_LENGTH;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly passwordForm = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.passwordForm.invalid || this.submitting()) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.error.set(null);
    this.submitting.set(true);
    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordForm.reset();
        void this.router.navigate(['/login']);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(
          error.status === 0 ? 'Cannot reach the server.' : (error.error?.message ?? 'Could not change the password.'),
        );
        this.submitting.set(false);
      },
    });
  }
}
