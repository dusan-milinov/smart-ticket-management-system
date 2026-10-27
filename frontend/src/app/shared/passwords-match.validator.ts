import { ValidatorFn } from '@angular/forms';

export const passwordsMatch: ValidatorFn = (control) =>
  control.get('password')?.value === control.get('confirmPassword')?.value ? null : { passwordMismatch: true };
