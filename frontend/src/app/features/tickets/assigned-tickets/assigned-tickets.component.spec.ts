import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AssignedTicketsComponent } from './assigned-tickets.component';
import { Ticket } from '../models/ticket.model';

const ticket: Ticket = {
  ticketId: 42, ticketNumber: 'TKT-TEST', title: 'Cannot connect to VPN', description: 'Connection times out.',
  ticketStatus: 'IN_QUEUE', ticketPriority: 'HIGH', ticketType: 'SOFTWARE_REQUEST', resolutionSummary: null,
  createdAt: '2026-10-26T10:00:00Z', updatedAt: '2026-10-26T10:00:00Z', creatorId: 11, assigneeId: 12,
  assigneeEmail: 'agent@example.com',
  creatorEmail: 'user@example.com',
};

describe('AssignedTicketsComponent', () => {
  let fixture: ComponentFixture<AssignedTicketsComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [AssignedTicketsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AssignedTicketsComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('shows submission time and an Open button that links to ticket details without changing the ticket', () => {
    http.expectOne('/api/tickets/assigned/status-counts').flush({ IN_QUEUE: 1 });
    http.expectOne((request) => request.url === '/api/tickets/assigned').flush({
      content: [ticket], number: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Submitted');
    expect(root.textContent).toContain('Oct 26, 2026');
    expect(root.querySelector('.status-in_queue')?.textContent).toBe('Pending');
    expect([...root.querySelectorAll('th')].map((cell) => cell.textContent?.trim())).toEqual([
      'Ticket #', 'Title', 'Priority', 'Type', 'Status', 'Submitted', 'Actions',
    ]);
    expect(root.querySelector('.tab')?.textContent?.trim()).toContain('1');
    const view = root.querySelector<HTMLAnchorElement>('.actions .btn');
    expect(view?.classList.contains('ticket-view')).toBe(true);
    expect(view?.textContent?.trim()).toBe('Open');
    expect(view?.getAttribute('aria-label')).toBe('Open ticket TKT-TEST');
    expect(view?.getAttribute('href')).toBe('/tickets/42');
    expect(http.match('/api/tickets/42/start')).toHaveLength(0);
  });

  it('combines status, priority, search and sorting on the server and preserves them on pagination', () => {
    http.expectOne('/api/tickets/assigned/status-counts').flush({ IN_QUEUE: 21 });
    const result = {
      content: [ticket], number: 0, size: 10, totalElements: 21, totalPages: 3, first: true, last: false, empty: false,
    };
    http.expectOne((r) => r.url === '/api/tickets/assigned').flush(result);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.tab:nth-child(2)')!.click();
    http.expectOne((r) => r.url === '/api/tickets/assigned').flush(result);
    const priority = root.querySelector<HTMLSelectElement>('select[aria-label="Filter by priority"]')!;
    priority.value = 'HIGH';
    priority.dispatchEvent(new Event('change'));
    const priorityCounts = http.expectOne((r) => r.url === '/api/tickets/assigned/status-counts');
    expect(priorityCounts.request.params.get('priority')).toBe('HIGH');
    expect(priorityCounts.request.params.has('status')).toBe(false);
    priorityCounts.flush({ IN_QUEUE: 21 });
    http.expectOne((r) => r.url === '/api/tickets/assigned').flush(result);
    const input = root.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = 'vpn';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    const searchCounts = http.expectOne((r) => r.url === '/api/tickets/assigned/status-counts');
    expect(searchCounts.request.params.get('search')).toBe('vpn');
    searchCounts.flush({ IN_QUEUE: 21 });
    http.expectOne((r) => r.url === '/api/tickets/assigned').flush(result);
    const sort = root.querySelector<HTMLSelectElement>('select[aria-label="Sort tickets"]')!;
    sort.value = 'oldest';
    sort.dispatchEvent(new Event('change'));
    const sorted = http.expectOne((r) => r.url === '/api/tickets/assigned');
    expect(sorted.request.params.get('page')).toBe('0');
    expect(sorted.request.params.getAll('sort')).toEqual(['createdAt,asc', 'ticketId,asc']);
    sorted.flush(result);
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('app-pagination button:last-child')!.click();
    const next = http.expectOne((r) => r.url === '/api/tickets/assigned');
    expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('status')).toBe('IN_QUEUE');
    expect(next.request.params.get('priority')).toBe('HIGH');
    expect(next.request.params.get('search')).toBe('vpn');
    expect(next.request.params.getAll('sort')).toEqual(['createdAt,asc', 'ticketId,asc']);
    next.flush({ ...result, number: 1 });
  });

  it('renders the server order when switching between newest and oldest on All', () => {
    http.expectOne('/api/tickets/assigned/status-counts').flush({ IN_QUEUE: 2 });
    const newer = { ...ticket, ticketId: 43, ticketNumber: 'TKT-NEW', createdAt: '2026-10-27T10:00:00Z' };
    const result = { content: [newer, ticket], number: 0, totalPages: 1 };
    const initial = http.expectOne((r) => r.url === '/api/tickets/assigned');
    expect(initial.request.params.getAll('sort')).toEqual(['createdAt,desc', 'ticketId,desc']);
    initial.flush(result);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const order = () => [...root.querySelectorAll('td.number')].map((cell) => cell.textContent);
    expect(order()).toEqual(['TKT-NEW', 'TKT-TEST']);
    const sort = root.querySelector<HTMLSelectElement>('select[aria-label="Sort tickets"]')!;
    sort.value = 'oldest';
    sort.dispatchEvent(new Event('change'));
    const oldest = http.expectOne((r) => r.url === '/api/tickets/assigned');
    expect(oldest.request.params.getAll('sort')).toEqual(['createdAt,asc', 'ticketId,asc']);
    oldest.flush({ ...result, content: [ticket, newer] });
    fixture.detectChanges();
    expect(order()).toEqual(['TKT-TEST', 'TKT-NEW']);
    sort.value = 'newest';
    sort.dispatchEvent(new Event('change'));
    const newest = http.expectOne((r) => r.url === '/api/tickets/assigned');
    expect(newest.request.params.getAll('sort')).toEqual(['createdAt,desc', 'ticketId,desc']);
    newest.flush(result);
    fixture.detectChanges();
    expect(order()).toEqual(['TKT-NEW', 'TKT-TEST']);
  });

  it('keeps filters and the list usable when counts fail, and retries counts separately', () => {
    http.expectOne('/api/tickets/assigned/status-counts').flush({}, { status: 500, statusText: 'Error' });
    http.expectOne((r) => r.url === '/api/tickets/assigned').flush({
      content: [ticket], number: 0, totalPages: 1,
    });
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain(ticket.title);
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Could not load ticket status counts.');
    root.querySelector<HTMLButtonElement>('.state-error button')!.click();
    http.expectOne('/api/tickets/assigned/status-counts').flush({ IN_QUEUE: 1 });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.querySelector('.tab-count')?.textContent).toBe('1');
  });

  it('cancels stale list and count requests when filters change rapidly', () => {
    const initialList = http.expectOne((r) => r.url === '/api/tickets/assigned');
    const initialCounts = http.expectOne('/api/tickets/assigned/status-counts');
    const root = fixture.nativeElement as HTMLElement;
    const priority = root.querySelector<HTMLSelectElement>('select[aria-label="Filter by priority"]')!;
    priority.value = 'HIGH';
    priority.dispatchEvent(new Event('change'));
    expect(initialList.cancelled).toBe(true);
    expect(initialCounts.cancelled).toBe(true);
    http.expectOne((r) => r.url === '/api/tickets/assigned/status-counts').flush({ IN_QUEUE: 0 });
    http.expectOne((r) => r.url === '/api/tickets/assigned').flush({ content: [], number: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(root.textContent).toContain('No tickets match your filters.');
  });
});
