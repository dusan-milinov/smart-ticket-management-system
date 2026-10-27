import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

import { passwordsMatch } from '../../shared/passwords-match.validator';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-register',
  styleUrls: ['../../shared/account-form.css', './register.component.css'],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly registrationError = signal<string | null>(null);
  readonly registrationSuccess = signal(false);

  readonly registerForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  onSubmit(): void {
    if (this.registerForm.invalid) {
      return;
    }

    this.registrationError.set(null);
    const { email, password } = this.registerForm.getRawValue();
    this.authService.register(email, password).subscribe({
      next: () => this.registrationSuccess.set(true),
      error: (error) => {
        console.error('Registration failed', error);
        this.registrationError.set('Registration failed. Please check your details and try again.');
      }
    });
  }
}
