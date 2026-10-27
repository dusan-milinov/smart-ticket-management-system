import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserDirectoryService } from './user-directory.service';

describe('UserDirectoryService', () => {
  let service: UserDirectoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserDirectoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests non-admin accounts with pagination and credentials', () => {
    service.getNonAdminUsers(1, 10).subscribe();

    const request = http.expectOne((req) => req.url === '/api/users');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ content: [], number: 1, size: 10, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('requests the administrator account directories', () => {
    service.getAllUsers().subscribe();
    http.expectOne((req) => req.url === '/api/admin/users').flush({ content: [] });

    service.getSupportAgents().subscribe();
    http.expectOne((req) => req.url === '/api/admin/support-agents').flush({ content: [] });
  });

  it('requests user details, their tickets, and deactivation with credentials', () => {
    service.getUser(4).subscribe();
    const details = http.expectOne('/api/users/4');
    expect(details.request.withCredentials).toBe(true);
    details.flush({});

    service.getUserTickets(4, 2, 5).subscribe();
    const tickets = http.expectOne((req) => req.url === '/api/users/4/tickets');
    expect(tickets.request.params.get('page')).toBe('2');
    expect(tickets.request.params.get('size')).toBe('5');
    tickets.flush({ content: [] });

    service.deactivateUser(4).subscribe();
    const deactivate = http.expectOne('/api/admin/users/4/deactivate');
    expect(deactivate.request.method).toBe('PATCH');
    expect(deactivate.request.withCredentials).toBe(true);
    deactivate.flush({});
  });
});

