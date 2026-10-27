import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MyTicketsComponent } from './my-tickets.component';

const emptyPage = { content: [], number: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true, empty: true };

describe('MyTicketsComponent', () => {
  let fixture: ComponentFixture<MyTicketsComponent>;
  let http: HttpTestingController;
  let element: HTMLElement;

  const listRequests = () => http.match((r) => r.url === '/api/tickets/my');
  const countRequests = () => http.match((r) => r.url === '/api/tickets/my/status-counts');
  const flushInitial = () => {
    http.expectOne((r) => r.url === '/api/tickets/my/status-counts')
      .flush({ IN_QUEUE: 2, FULFILLMENT: 1, RESOLVED: 0, CANCELLED: 3 });
    http.expectOne((r) => r.url === '/api/tickets/my').flush(emptyPage);
    fixture.detectChanges();
  };
  const type = (value: string) => {
    const input = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const tab = (name: string) =>
    [...element.querySelectorAll<HTMLButtonElement>('.tab')].find((t) => t.textContent!.includes(name))!;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [MyTicketsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MyTicketsComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('starts on All with no filters and shows the totals', () => {
    const list = http.expectOne((r) => r.url === '/api/tickets/my');
    expect(list.request.params.keys()).toEqual(['page', 'size', 'sort']);
    expect(list.request.params.getAll('sort')).toEqual(['createdAt,desc', 'ticketId,desc']);
    list.flush(emptyPage);
    http.expectOne((r) => r.url === '/api/tickets/my/status-counts').flush({ IN_QUEUE: 2, FULFILLMENT: 1, RESOLVED: 0, CANCELLED: 3 });
    fixture.detectChanges();

    expect(tab('All').getAttribute('aria-selected')).toBe('true');
    expect([...element.querySelectorAll('.tab')].map((t) => t.textContent!.trim().replace(/\s+/g, ' '))).toEqual([
      'All 6', 'Pending 2', 'In Progress 1', 'Resolved 0', 'Cancelled 3',
    ]);
    expect([...element.querySelectorAll<HTMLOptionElement>('select[aria-label="Filter by priority"] option')].map((o) => o.value)).toEqual(['', 'LOW', 'MEDIUM', 'HIGH']);
    expect(element.textContent).toContain('You have not submitted any tickets yet.');
  });

  it('shows ticket details without offering cancellation from the list', () => {
    http.expectOne((r) => r.url === '/api/tickets/my/status-counts')
      .flush({ IN_QUEUE: 1 });
    http.expectOne((r) => r.url === '/api/tickets/my').flush({
      ...emptyPage,
      content: [{
        ticketId: 42,
        ticketNumber: 'T-42',
        title: 'Cannot sign in',
        description: '',
        ticketStatus: 'IN_QUEUE',
        ticketPriority: 'MEDIUM',
        resolutionSummary: null,
        createdAt: '2026-10-27T12:00:00Z',
        updatedAt: '2026-10-27T12:00:00Z',
        creatorId: 1,
        assigneeId: null,
        ticketType: 'TECHNICAL',
      }],
    });
    fixture.detectChanges();

    expect(element.querySelector('.actions a')?.textContent?.trim()).toBe('View');
    expect(element.querySelector('button.btn-warn')).toBeNull();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
  });

  it('requests the selected backend status from the first page without refetching counts', () => {
    flushInitial();
    tab('Pending').click();
    fixture.detectChanges();

    const [list] = listRequests();
    expect(list.request.params.get('status')).toBe('IN_QUEUE');
    expect(list.request.params.get('page')).toBe('0');
    expect(countRequests()).toHaveLength(0);
    list.flush(emptyPage);
    fixture.detectChanges();
    expect(tab('Pending').getAttribute('aria-selected')).toBe('true');
    expect(element.textContent).toContain('No tickets match your filters.');
  });

  it('debounces search and combines it with the status and priority filters', () => {
    flushInitial();
    tab('Resolved').click();
    listRequests()[0].flush(emptyPage);
    fixture.detectChanges();

    type('v');
    vi.advanceTimersByTime(200);
    type('vpn ');
    vi.advanceTimersByTime(299);
    expect(listRequests()).toHaveLength(0);
    vi.advanceTimersByTime(1);

    const select = element.querySelector('select')!;
    const [search] = listRequests();
    expect(search.request.params.get('search')).toBe('vpn');
    expect(search.request.params.get('status')).toBe('RESOLVED');
    expect(search.request.params.get('page')).toBe('0');
    countRequests()[0].flush({});
    search.flush(emptyPage);

    select.value = 'HIGH';
    select.dispatchEvent(new Event('change'));
    const [priority] = listRequests();
    expect(priority.request.params.get('priority')).toBe('HIGH');
    expect(priority.request.params.get('search')).toBe('vpn');
    expect(priority.request.params.get('status')).toBe('RESOLVED');
    const [counts] = countRequests();
    expect(counts.request.params.get('priority')).toBe('HIGH');
    expect(counts.request.params.get('search')).toBe('vpn');
    expect(counts.request.params.has('status')).toBe(false);
    counts.flush({});
    priority.flush(emptyPage);
  });

  it('clears the search immediately and ignores the pending debounce', () => {
    flushInitial();
    type('abc');
    element.querySelector<HTMLButtonElement>('.search-clear')!.click();
    vi.advanceTimersByTime(500);
    expect(listRequests()).toHaveLength(0);

    type('abc');
    vi.advanceTimersByTime(300);
    listRequests()[0].flush(emptyPage);
    countRequests()[0].flush({});
    fixture.detectChanges();

    element.querySelector<HTMLButtonElement>('.search-clear')!.click();
    fixture.detectChanges();
    const [list] = listRequests();
    expect(list.request.params.has('search')).toBe(false);
    expect(element.querySelector<HTMLInputElement>('input[type="search"]')!.value).toBe('');
    list.flush(emptyPage);
    countRequests()[0].flush({});
  });

  it('shows an error with retry while keeping the filter controls', () => {
    http.expectOne((r) => r.url === '/api/tickets/my/status-counts').flush({}, { status: 500, statusText: 'Error' });
    http.expectOne((r) => r.url === '/api/tickets/my').flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load your tickets.');
    expect(element.querySelector('.tabs')).not.toBeNull();
    expect(element.querySelector('.tab-count')).toBeNull();
    element.querySelector<HTMLButtonElement>('.state-error .btn')!.click();
    listRequests()[0].flush(emptyPage);
  });

  it('sorts from the first page without changing filters or fetching counts again', () => {
    flushInitial();
    tab('Pending').click();
    listRequests()[0].flush({ ...emptyPage, number: 2, totalPages: 4 });
    fixture.detectChanges();

    const sort = element.querySelector<HTMLSelectElement>('select[aria-label="Sort tickets"]')!;
    sort.value = 'oldest';
    sort.dispatchEvent(new Event('change'));
    const [list] = listRequests();
    expect(list.request.params.get('page')).toBe('0');
    expect(list.request.params.get('status')).toBe('IN_QUEUE');
    expect(list.request.params.getAll('sort')).toEqual(['createdAt,asc', 'ticketId,asc']);
    expect(countRequests()).toHaveLength(0);
    list.flush(emptyPage);
  });
});
