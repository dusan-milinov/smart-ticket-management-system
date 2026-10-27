import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.component.css',
  templateUrl: './login.component.html',
})
export class LoginComponent {

  private readonly router = inject(Router);
  
  private readonly authService = inject(AuthService);

  private readonly fb = inject(FormBuilder);

  readonly loginError = signal<string | null>(null);

  loginForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  onSubmit() {
    if (this.loginForm.invalid) {
      return;
    }
    this.loginError.set(null);
    const { email, password } = this.loginForm.getRawValue();
    this.authService.login(email, password).subscribe({
      next: () => {
             this.authService.getCurrentUser().subscribe({
              next: () => this.router.navigate(['/dashboard']),
              error: (error) => {
                console.error('Could not load the current user', error);
              }
             })
      },
      error: (error) => {
        console.error('Login failed', error);
        this.loginError.set('Login failed. Please check your credentials and try again.');
      }
    });
  }

}
