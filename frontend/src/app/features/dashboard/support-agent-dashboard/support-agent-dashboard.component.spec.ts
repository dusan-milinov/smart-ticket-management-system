import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SupportAgentDashboardComponent } from './support-agent-dashboard.component';

describe('SupportAgentDashboardComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows a green Open button for each priority ticket without changing its status', () => {
    TestBed.configureTestingModule({
      imports: [SupportAgentDashboardComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(SupportAgentDashboardComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/tickets/assigned/status-counts').flush({ IN_QUEUE: 1, FULFILLMENT: 1 });
    http.expectOne('/api/tickets/assigned/priority-queue').flush([
      { ticketId: 42, ticketNumber: 'TKT-0042', title: 'VPN connection drops', ticketPriority: 'HIGH', ticketStatus: 'IN_QUEUE' },
      { ticketId: 43, ticketNumber: 'TKT-0043', title: 'Install the reporting tool', ticketPriority: 'MEDIUM', ticketStatus: 'FULFILLMENT' },
    ]);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    const actions = Array.from(element.querySelectorAll<HTMLAnchorElement>('.queue-view'));
    expect(actions.map(action => action.textContent?.trim())).toEqual(['Open', 'Open']);
    expect(actions.map(action => action.getAttribute('href'))).toEqual(['/tickets/42', '/tickets/43']);
    expect(actions.map(action => action.getAttribute('aria-label')))
      .toEqual(['Open ticket TKT-0042', 'Open ticket TKT-0043']);
    expect(actions.every(action => action.classList.contains('btn'))).toBe(true);
    http.expectNone(request => request.method !== 'GET');
  });
});
