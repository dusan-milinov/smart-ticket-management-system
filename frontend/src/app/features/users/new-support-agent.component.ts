import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { passwordsMatch } from '../../shared/passwords-match.validator';
import { MIN_PASSWORD_LENGTH } from '../profile/profile.component';
import { UserDirectoryService } from './user-directory.service';

@Component({
  selector: 'app-new-support-agent',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './new-support-agent.component.html',
  styleUrls: ['../../shared/account-form.css', './new-support-agent.component.css'],
})
export class NewSupportAgentComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly directoryService = inject(UserDirectoryService);
  private readonly router = inject(Router);

  protected readonly minimumPasswordLength = MIN_PASSWORD_LENGTH;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password } = this.form.getRawValue();
    this.error.set(null);
    this.submitting.set(true);
    this.directoryService.createSupportAgent(email.trim(), password).subscribe({
      next: () => void this.router.navigate(['/users'], { queryParams: { list: 'agents' } }),
      error: (error: HttpErrorResponse) => {
        this.error.set(
          error.status === 0 ? 'Cannot reach the server.' : (error.error?.message ?? 'Could not create the support agent.'),
        );
        this.submitting.set(false);
      },
    });
  }
}
