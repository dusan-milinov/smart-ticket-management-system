import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AssignedTicketsComponent } from './assigned-tickets.component';
import { Page } from '../models/page.model';
import { Ticket } from '../models/ticket.model';

const ticket: Ticket = {
  ticketId: 42,
  ticketNumber: 'TKT-TEST',
  title: 'Cannot connect to VPN',
  description: 'Connection times out.',
  ticketStatus: 'FULFILLMENT',
  ticketPriority: 'HIGH',
  ticketType: 'SOFTWARE_REQUEST',
  resolutionSummary: null,
  createdAt: '2026-10-26T10:00:00Z',
  updatedAt: '2026-10-26T10:00:00Z',
  creatorId: 11,
  assigneeId: 12,
};

function result(content: Ticket[], number = 0, totalPages = 1): Page<Ticket> {
  return {
    content, number, size: 10, totalPages,
    totalElements: totalPages > 1 ? 11 : content.length,
    first: number === 0, last: number === totalPages - 1, empty: content.length === 0,
  };
}

describe('AssignedTicketsComponent', () => {
  let fixture: ComponentFixture<AssignedTicketsComponent>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignedTicketsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AssignedTicketsComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function request(page = 0) {
    return http.expectOne(
      (r) => r.url === '/api/tickets/assigned'
        && r.params.get('page') === String(page)
        && r.params.get('size') === '10',
    );
  }

  it('shows loading then renders ticket fields and only the Open action', () => {
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Loading tickets');
    request().flush(result([ticket]));
    fixture.detectChanges();

    expect(element.textContent).toContain('Assigned Tickets');
    expect(element.textContent).toContain('TKT-TEST');
    expect(element.textContent).toContain('Cannot connect to VPN');
    expect(element.textContent).toContain('Software request');
    expect(element.textContent).toContain('Oct 26, 2026');
    expect(element.querySelector('.status-fulfillment')?.textContent).toBe('Fulfillment');
    expect(element.querySelector('.priority-high')?.textContent).toBe('High');
    expect(element.querySelector('.btn-open')?.textContent?.trim()).toBe('Open');
    expect(element.querySelectorAll('.actions button').length).toBe(1);
    expect(element.textContent).not.toContain('Resolve ticket');
    expect(element.textContent).not.toContain('Cancel ticket');
    expect(element.querySelector('[role="status"]')).toBeNull();
  });

  it('shows an empty state without pagination', () => {
    request().flush(result([], 0, 0));
    fixture.detectChanges();

    expect(element.textContent).toContain('You do not have any assigned tickets yet.');
    expect(element.querySelector('nav[aria-label="Pagination"]')).toBeNull();
  });

  it('requests the next page and retries that page after a server error', () => {
    request().flush(result([ticket], 0, 2));
    fixture.detectChanges();
    const next = Array.from(element.querySelectorAll('button')).find((b) => b.textContent === 'Next');
    expect(next).toBeDefined();
    next!.click();
    fixture.detectChanges();
    request(1).flush({ message: 'Please try later.' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Please try later.');
    element.querySelector<HTMLButtonElement>('.state-error button')!.click();
    fixture.detectChanges();
    request(1).flush(result([ticket], 1, 2));
    fixture.detectChanges();

    expect(element.textContent).toContain('Page 2 of 2');
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows a useful message for a network error and allows retrying', () => {
    request().error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Cannot reach the server.');
    element.querySelector<HTMLButtonElement>('.state-error button')!.click();
    fixture.detectChanges();
    request().flush(result([ticket]));
    fixture.detectChanges();
    expect(element.textContent).toContain('TKT-TEST');
  });

  it('shows a fallback message when the server provides no message', () => {
    request().flush({}, { status: 500, statusText: 'Server error' });
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')?.textContent)
      .toContain('Could not load your assigned tickets.');
  });

  it('shows only an Open button for every ticket regardless of status', () => {
    request().flush(result([
      ticket,
      { ...ticket, ticketId: 43, ticketStatus: 'RESOLVED' },
      { ...ticket, ticketId: 44, ticketStatus: 'CANCELLED' },
    ]));
    fixture.detectChanges();
    expect(element.querySelectorAll('.btn-open').length).toBe(3);
    expect(element.querySelectorAll('.actions button').length).toBe(3);
  });

  it('opens the selected ticket details page', () => {
    request().flush(result([ticket]));
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    element.querySelector<HTMLButtonElement>('.btn-open')!.click();

    expect(navigate).toHaveBeenCalledWith(['/tickets', ticket.ticketId]);
  });

  it('starts an in-queue ticket before opening its details page', () => {
    const queuedTicket = { ...ticket, ticketStatus: 'IN_QUEUE' };
    request().flush(result([queuedTicket]));
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    element.querySelector<HTMLButtonElement>('.btn-open')!.click();
    fixture.detectChanges();

    const start = http.expectOne(`/api/tickets/${ticket.ticketId}/start`);
    expect(start.request.method).toBe('PATCH');
    expect(element.querySelector<HTMLButtonElement>('.btn-open')?.disabled).toBe(true);
    expect(element.querySelector('.btn-open')?.textContent).toContain('Opening');

    start.flush({ ...queuedTicket, ticketStatus: 'FULFILLMENT' });
    fixture.detectChanges();

    expect(element.querySelector('.status-fulfillment')?.textContent).toBe('Fulfillment');
    expect(navigate).toHaveBeenCalledWith(['/tickets', ticket.ticketId]);
  });

  it('reports start errors and stays on the assigned tickets page', () => {
    const queuedTicket = { ...ticket, ticketStatus: 'IN_QUEUE' };
    request().flush(result([queuedTicket]));
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    element.querySelector<HTMLButtonElement>('.btn-open')!.click();
    http.expectOne(`/api/tickets/${ticket.ticketId}/start`)
      .flush({ message: 'Ticket is no longer available.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Ticket is no longer available.');
    expect(navigate).not.toHaveBeenCalled();
    expect(element.querySelector<HTMLButtonElement>('.btn-open')?.disabled).toBe(false);
  });

});
