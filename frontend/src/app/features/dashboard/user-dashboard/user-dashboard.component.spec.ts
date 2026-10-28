import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Ticket } from '../../tickets/models/ticket.model';
import { UserDashboardComponent } from './user-dashboard.component';

describe('UserDashboardComponent', () => {
  let fixture: ComponentFixture<UserDashboardComponent>;
  let http: HttpTestingController;

  const ticket: Ticket = {
    ticketId: 42,
    ticketNumber: 'TKT-0042',
    title: 'VPN disconnects during meetings',
    description: 'Connection drops after a few minutes.',
    ticketStatus: 'FULFILLMENT',
    ticketPriority: 'HIGH',
    ticketType: 'INCIDENT',
    resolutionSummary: null,
    creatorId: 5,
    creatorEmail: 'user@example.com',
    assigneeId: 8,
    assigneeEmail: 'agent@example.com',
    createdAt: '2026-10-27T09:00:00Z',
    updatedAt: '2026-10-27T09:01:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserDashboardComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserDashboardComponent);
  });

  afterEach(() => http.verify());

  it('shows a distinct View link for each recent ticket while preserving its details and the full total', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/my/status-counts').flush({ FULFILLMENT: 5, RESOLVED: 4 });
    const request = http.expectOne((request) => request.url === '/api/tickets/my');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('5');
    expect(request.request.params.getAll('sort')).toEqual(['createdAt,desc', 'ticketId,desc']);
    request.flush({
      content: [ticket, { ...ticket, ticketId: 41, ticketNumber: 'TKT-0041' }],
      totalElements: 2,
    });
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    const viewLinks = Array.from(element.querySelectorAll<HTMLAnchorElement>('.ticket-view'));
    expect(viewLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/tickets/42',
      '/tickets/41',
    ]);
    expect(viewLinks.map((link) => link.getAttribute('aria-label'))).toEqual([
      'View ticket TKT-0042',
      'View ticket TKT-0041',
    ]);
    expect(viewLinks.every((link) => link.textContent?.trim() === 'View')).toBe(true);
    expect(element.querySelector('.ticket-title')?.textContent).toBe(ticket.title);
    expect(element.querySelector('.ticket-meta')?.textContent).toContain('In Progress');
    expect(element.querySelector('.ticket-meta')?.textContent).toContain('High');
    expect(element.querySelector('time')?.getAttribute('datetime')).toBe(ticket.createdAt);
    expect(element.querySelector('.summary-total')?.textContent).toBe('9');
    expect(element.querySelector('.summary-open')?.textContent).toContain('5 currently open');
    expect(element.querySelector('[data-status="FULFILLMENT"] .status-value')?.textContent).toBe('5');
    expect(element.querySelector('[data-status="RESOLVED"] .status-value')?.textContent).toBe('4');
  });

  it('offers retry after a failed load and renders no View links for an empty dashboard', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/my/status-counts').flush({});
    http
      .expectOne((request) => request.url === '/api/tickets/my')
      .flush({}, { status: 500, statusText: 'Server error' });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[role=alert]')).not.toBeNull();
    expect(element.querySelector('.ticket-view')).toBeNull();

    element.querySelector<HTMLButtonElement>('.retry-button')!.click();
    http.expectOne((request) => request.url === '/api/tickets/my').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(element.textContent).toContain('A clear slate');
    expect(element.querySelector('.ticket-view')).toBeNull();
    expect(element.querySelector('[role=alert]')).toBeNull();
  });

  it('keeps the overview total unfiltered and distinguishes no matches from no tickets', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/my/status-counts').flush({ IN_QUEUE: 4, RESOLVED: 2 });
    const initial = http.expectOne((request) => request.url === '/api/tickets/my');
    expect(initial.request.params.get('size')).toBe('5');
    initial.flush({ content: [], totalElements: 6 });
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    element.querySelector<HTMLButtonElement>('.tab:nth-child(3)')!.click();
    const filtered = http.expectOne((request) => request.url === '/api/tickets/my');
    expect(filtered.request.params.get('status')).toBe('FULFILLMENT');
    filtered.flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(element.querySelector('.summary-total')?.textContent?.trim()).toBe('6');
    expect(element.querySelector('[data-status="IN_QUEUE"] .status-value')?.textContent).toBe('4');
    expect(element.querySelector('[data-status="FULFILLMENT"] .status-value')?.textContent).toBe('0');
    expect(element.textContent).toContain('No matching requests');
    expect(element.textContent).not.toContain('Create your first ticket');
    expect(element.querySelector('.ticket-view')).toBeNull();
    expect(element.querySelector('#recent-heading')?.textContent).toBe('Matching tickets');
  });

  it('labels the recent ticket action View and links to the ticket details', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/my/status-counts').flush({ IN_QUEUE: 1 });
    http.expectOne((request) => request.url === '/api/tickets/my').flush({
      content: [{ ...ticket, ticketId: 17, ticketNumber: 'ST-0017', ticketStatus: 'IN_QUEUE' }],
      totalElements: 1,
    });
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.ticket-title')?.tagName).toBe('SPAN');
    expect(element.querySelectorAll('.ticket-row a')).toHaveLength(1);
    const action = element.querySelector<HTMLAnchorElement>('.ticket-view');
    expect(action?.classList.contains('btn')).toBe(true);
    expect(action?.textContent?.trim()).toBe('View');
    expect(action?.getAttribute('aria-label')).toBe('View ticket ST-0017');
    expect(action?.getAttribute('href')).toBe('/tickets/17');
  });
});
