import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UserDashboardComponent } from './user-dashboard.component';

describe('UserDashboardComponent ticket controls', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('labels the recent ticket action View and links to the ticket details', () => {
    TestBed.configureTestingModule({
      imports: [UserDashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(UserDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/tickets/my/status-counts').flush({ IN_QUEUE: 1 });
    http.expectOne((r) => r.url === '/api/tickets/my').flush({
      content: [{
        ticketId: 17,
        ticketNumber: 'ST-0017',
        title: 'Account access',
        ticketStatus: 'IN_QUEUE',
        ticketPriority: 'HIGH',
        createdAt: '2026-10-25T10:00:00Z',
      }],
      totalElements: 1,
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.ticket-title')?.tagName).toBe('SPAN');
    expect(root.querySelectorAll('.ticket-row a').length).toBe(1);
    const action = root.querySelector<HTMLAnchorElement>('.ticket-open');
    expect(action?.classList.contains('btn')).toBe(true);
    expect(action?.textContent?.trim()).toBe('View');
    expect(action?.getAttribute('aria-label')).toBe('View ST-0017');
    expect(action?.getAttribute('href')).toBe('/tickets/17');
  });

  it('keeps the overview total unfiltered and distinguishes no matches from no tickets', () => {
    TestBed.configureTestingModule({
      imports: [UserDashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(UserDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/tickets/my/status-counts').flush({ IN_QUEUE: 4, RESOLVED: 2 });
    const initial = http.expectOne((r) => r.url === '/api/tickets/my');
    expect(initial.request.params.get('size')).toBe('5');
    initial.flush({ content: [], totalElements: 6 });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.tab:nth-child(3)')!.click();
    const filtered = http.expectOne((r) => r.url === '/api/tickets/my');
    expect(filtered.request.params.get('status')).toBe('FULFILLMENT');
    filtered.flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(root.querySelector('.summary-total')?.textContent?.trim()).toBe('6');
    expect(root.querySelector('.status-card[data-status="IN_QUEUE"] .status-value')?.textContent?.trim()).toBe('4');
    expect(root.querySelector('.status-card[data-status="FULFILLMENT"] .status-value')?.textContent?.trim()).toBe('0');
    expect(root.textContent).toContain('No matching requests');
    expect(root.textContent).not.toContain('Create your first ticket');
    expect(root.querySelector('#recent-heading')?.textContent).toBe('Matching tickets');
  });
});
