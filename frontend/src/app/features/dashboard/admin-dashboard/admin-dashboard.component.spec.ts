import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { routes } from '../../../app.routes';
import { AuthService } from '../../../core/auth.service';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { AdminDashboardSummary } from './admin-dashboard.service';

const summary: AdminDashboardSummary = {
  totalTickets: 28,
  ticketsByStatus: { IN_QUEUE: 2, FULFILLMENT: 12, RESOLVED: 10, CANCELLED: 4 },
  openTicketsByPriority: { HIGH: 5, MEDIUM: 6, LOW: 3 },
  unassignedOpenTickets: 2, totalAccounts: 10, regularUsers: 4, supportAgents: 5, activeSupportAgents: 3,
  recentTickets: [{ ticketId: 28, ticketNumber: 'TKT-SEED-0028', title: 'Install the review tool',
    ticketStatus: 'RESOLVED', ticketPriority: 'HIGH', ticketType: 'SOFTWARE_REQUEST', description: 'Approved tool',
    resolutionSummary: 'Installed', creatorId: 2, creatorEmail: 'user@example.com', assigneeId: 3, assigneeEmail: 'agent@example.com', createdAt: '2026-01-29T09:00:00Z', updatedAt: '2026-01-29T09:08:00Z' }],
};

describe('AdminDashboardComponent', () => {
  let fixture: ComponentFixture<AdminDashboardComponent>;
  let http: HttpTestingController;
  const user = signal({ email: 'admin@example.com', role: 'ROLE_ADMIN' });

  beforeEach(async () => {
    user.set({ email: 'admin@example.com', role: 'ROLE_ADMIN' });
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: { user } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminDashboardComponent);
  });

  afterEach(() => http.verify());

  it('loads real totals, computes open work, and links recent tickets and team actions', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading your support overview');
    const request = http.expectOne('/api/admin/dashboard');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush(summary);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-stat=total] .metric-value')?.textContent).toBe('28');
    expect(element.querySelector('[data-stat=open] .metric-value')?.textContent).toBe('14');
    expect(Array.from(element.querySelectorAll('.status-list dt'), item => item.textContent?.trim()))
      .toEqual(['Pending', 'In Progress', 'Resolved', 'Cancelled']);
    expect(element.querySelector('[data-stat=open] .metric-caption')?.textContent)
      .toContain('2 Pending · 12 In Progress');
    expect(element.querySelector('[data-stat=resolved]')?.textContent).toContain('35.7%');
    expect(element.querySelector('[data-stat=agents] .metric-value')?.textContent).toBe('3');
    expect(element.querySelector('[data-stat=agents] .metric-caption')?.textContent)
      .toBe('Active members of your support team');
    expect(element.querySelector('[data-stat=agents] a')).toBeNull();
    expect(element.querySelector('.assignment-note')).toBeNull();
    const viewLink = element.querySelector<HTMLAnchorElement>('.ticket-view');
    expect(viewLink?.textContent?.trim()).toBe('View');
    const destination = new URL(viewLink!.getAttribute('href')!, 'http://localhost');
    expect(destination.pathname).toBe('/tickets/28');
    expect(destination.searchParams.get('returnTo')).toBe('/dashboard');
    expect(viewLink?.getAttribute('aria-label')).toBe('View ticket TKT-SEED-0028');
    expect(element.querySelector('.ticket-title')?.textContent).toBe('Install the review tool');
    expect(element.querySelector('.ticket-title')?.tagName).toBe('SPAN');
    expect(element.querySelectorAll('.ticket-row a')).toHaveLength(1);
    expect(element.querySelector('.ticket-row .status-resolved')?.textContent).toBe('Resolved');
    expect(element.querySelector('.ticket-row .priority-high')?.textContent).toBe('High');
    expect(element.querySelector('.team-counts')?.textContent).toContain('Regular users4');
    expect(element.querySelector('.header-actions button')).toBeNull();
    expect(element.querySelector('.header-actions a')?.getAttribute('href')).toBe('/users/new-agent');
  });

  it('renders empty data without invalid percentages', () => {
    fixture.detectChanges();
    http.expectOne('/api/admin/dashboard').flush({ ...summary, totalTickets: 0, recentTickets: [],
      ticketsByStatus: { IN_QUEUE: 0, FULFILLMENT: 0, RESOLVED: 0, CANCELLED: 0 },
      openTicketsByPriority: { HIGH: 0, MEDIUM: 0, LOW: 0 }, unassignedOpenTickets: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No tickets yet.');
    expect(fixture.nativeElement.textContent).not.toMatch(/NaN|Infinity/);
    expect(fixture.nativeElement.querySelector('[data-stat=resolved]').textContent).toContain('0%');
  });

  it('shows a retryable error instead of misleading zero totals', () => {
    fixture.detectChanges();
    http.expectOne('/api/admin/dashboard').flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.metrics')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    fixture.nativeElement.querySelector('.state button').click();
    http.expectOne('/api/admin/dashboard').flush(summary);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-stat=total] .metric-value').textContent).toBe('28');
  });

  it('matches the admin dashboard route only for administrators', () => {
    const adminRoute = routes.find(route => route.children)?.children?.find(route => route.path === 'dashboard')!;
    const guard = adminRoute.canMatch![0] as () => boolean;
    for (const role of ['ROLE_USER', 'ROLE_SUPPORT_AGENT', 'ROLE_ADMIN']) {
      user.set({ email: 'test@example.com', role });
      expect(TestBed.runInInjectionContext(guard)).toBe(role === 'ROLE_ADMIN');
    }
  });
});
