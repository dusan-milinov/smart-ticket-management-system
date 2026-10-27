import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TicketService } from './ticket.service';

describe('TicketService', () => {
  let service: TicketService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TicketService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should load ticket comments with session credentials', () => {
    service.getTicketComments(42).subscribe();

    const req = http.expectOne('/api/comments/42');
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBe(true);
    req.flush([]);
  });

  it('should post only the ticket ID and content with session credentials', () => {
    service.postTicketComment(42, 'First line\nSecond line').subscribe();

    const req = http.expectOne('/api/comments');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toEqual({ ticketId: 42, content: 'First line\nSecond line' });
    req.flush({ id: 1, ticketId: 42, authorId: 7, authorEmail: 'user@example.com', authorRole: 'USER', content: 'First line\nSecond line', createdAt: '2026-10-27T12:00:00Z' });
  });

  it('should request the current user tickets with paging', () => {
    service.getMyTickets(2, 10).subscribe();

    const req = http.expectOne((r) => r.url === '/api/tickets/my');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [], number: 2, size: 10, totalElements: 0, totalPages: 0, first: false, last: true, empty: true });
  });

  it('should send only the active my-ticket filters', () => {
    service.getMyTickets(0, 10, { status: 'RESOLVED', priority: 'LOW', search: 'vpn' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/tickets/my');
    expect(req.request.params.get('status')).toBe('RESOLVED');
    expect(req.request.params.get('priority')).toBe('LOW');
    expect(req.request.params.get('search')).toBe('vpn');
    req.flush({ content: [] });
  });

  it('should request my-ticket status counts', () => {
    service.getMyTicketStatusCounts({ priority: 'HIGH' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/tickets/my/status-counts');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.params.get('priority')).toBe('HIGH');
    expect(req.request.params.has('search')).toBe(false);
    req.flush({});
  });

  it('should resolve a ticket with the backend query parameters and credentials', () => {
    const summary = 'Restored VPN access & verified with user.';
    service.resolveTicket(42, summary).subscribe();

    const req = http.expectOne((r) => r.url === '/api/tickets/resolve');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toBeNull();
    expect(req.request.params.get('ticketID')).toBe('42');
    expect(req.request.params.get('summary')).toBe(summary);
    req.flush({ ticketId: 42, ticketStatus: 'RESOLVED', resolutionSummary: summary });
  });

  it('should request the signed-in agent tickets with paging and credentials', () => {
    service.getAssignedTickets(2, 10).subscribe();

    const req = http.expectOne((r) => r.url === '/api/tickets/assigned');
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], number: 2, size: 10, totalElements: 0, totalPages: 0, first: false, last: true, empty: true });
  });

  it('requests assigned status counts with search and priority, not status or sort', () => {
    const filters = { status: 'IN_QUEUE', sort: 'oldest', priority: 'HIGH', search: 'vpn' } as const;
    service.getAssignedTicketStatusCounts(filters).subscribe();
    const request = http.expectOne((r) => r.url === '/api/tickets/assigned/status-counts');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.params.keys()).toEqual(['priority', 'search']);
    expect(request.request.params.get('priority')).toBe('HIGH');
    expect(request.request.params.get('search')).toBe('vpn');
    request.flush({ IN_QUEUE: 2 });
  });
});
