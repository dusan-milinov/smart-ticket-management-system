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
    assigneeId: 8,
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
    const request = http.expectOne('/api/tickets/my?page=0&size=5');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ content: [ticket, { ...ticket, ticketId: 41, ticketNumber: 'TKT-0041' }], totalElements: 9 });
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    const viewLinks = Array.from(element.querySelectorAll<HTMLAnchorElement>('.ticket-view'));
    expect(viewLinks.map(link => link.getAttribute('href'))).toEqual(['/tickets/42', '/tickets/41']);
    expect(viewLinks.map(link => link.getAttribute('aria-label')))
      .toEqual(['View ticket TKT-0042', 'View ticket TKT-0041']);
    expect(viewLinks.every(link => link.textContent?.trim() === 'View')).toBe(true);
    expect(element.querySelector('.ticket-title')?.textContent).toBe(ticket.title);
    expect(element.querySelector('.ticket-meta')?.textContent).toContain('Fulfillment');
    expect(element.querySelector('.ticket-meta')?.textContent).toContain('High');
    expect(element.querySelector('time')?.getAttribute('datetime')).toBe(ticket.createdAt);
    expect(element.querySelector('.total-number')?.textContent).toBe('9');
  });

  it('offers retry after a failed load and renders no View links for an empty dashboard', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/my?page=0&size=5').flush({}, { status: 500, statusText: 'Server error' });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[role=alert]')).not.toBeNull();
    expect(element.querySelector('.ticket-view')).toBeNull();

    element.querySelector<HTMLButtonElement>('.retry-button')!.click();
    http.expectOne('/api/tickets/my?page=0&size=5').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(element.textContent).toContain('You have not submitted any tickets yet.');
    expect(element.querySelector('.ticket-view')).toBeNull();
    expect(element.querySelector('[role=alert]')).toBeNull();
  });
});
