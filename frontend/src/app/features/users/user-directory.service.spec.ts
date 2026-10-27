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

  it('uses the administrator directory endpoints with pagination and credentials', () => {
    service.getAllUsers(1, 10).subscribe();
    const users = http.expectOne((request) => request.url === '/api/admin/users');
    expect(users.request.params.get('page')).toBe('1');
    expect(users.request.params.get('size')).toBe('10');
    expect(users.request.withCredentials).toBe(true);
    users.flush({ content: [] });

    service.getSupportAgents().subscribe();
    http.expectOne((request) => request.url === '/api/admin/support-agents').flush({ content: [] });
  });

  it('requests account details, tickets, deactivation, and agent creation with credentials', () => {
    service.getUser(4).subscribe();
    http.expectOne('/api/users/4').flush({});

    service.getUserTickets(4, 2, 5).subscribe();
    const tickets = http.expectOne((request) => request.url === '/api/users/4/tickets');
    expect(tickets.request.params.get('page')).toBe('2');
    expect(tickets.request.params.get('size')).toBe('5');
    tickets.flush({ content: [] });

    service.deactivateUser(4).subscribe();
    const deactivate = http.expectOne('/api/admin/users/4/deactivate');
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({});

    service.createSupportAgent('agent@example.com', 'password123').subscribe();
    const create = http.expectOne('/api/admin/support-agents');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ email: 'agent@example.com', password: 'password123' });
    expect(create.request.withCredentials).toBe(true);
    create.flush({});
  });
});
