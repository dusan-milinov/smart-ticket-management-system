import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('changes the password with credentials and clears the authenticated user after success', () => {
    service.getCurrentUser().subscribe();
    http.expectOne('/api/users/me').flush({ email: 'user@example.com', role: 'ROLE_USER' });
    expect(service.user()?.email).toBe('user@example.com');

    service.changePassword('old-password', 'new-password').subscribe();
    const request = http.expectOne('/api/auth/change-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ currentPassword: 'old-password', newPassword: 'new-password' });
    request.flush(null);

    expect(service.user()).toBeNull();
  });
});
