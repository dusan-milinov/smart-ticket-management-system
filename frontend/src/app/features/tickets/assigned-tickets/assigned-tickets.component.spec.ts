import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
      providers: [provideHttpClient(), provideHttpClientTesting()],
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

  it('shows loading then renders ticket fields and badges without user cancellation actions', () => {
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
    expect(element.textContent).toContain('View Details');
    expect(element.textContent).toContain('Resolve Ticket');
    expect(element.textContent).not.toContain('Cancel Ticket');
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

  async function openResolve() {
    request().flush(result([ticket]));
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.btn-resolve')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function enterResolution(text: string) {
    const textarea = element.querySelector<HTMLTextAreaElement>('textarea')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submitResolution() {
    element.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  }

  it('only offers resolution on open tickets but always offers View Details', () => {
    request().flush(result([
      ticket,
      { ...ticket, ticketId: 43, ticketStatus: 'RESOLVED' },
      { ...ticket, ticketId: 44, ticketStatus: 'CANCELLED' },
    ]));
    fixture.detectChanges();
    expect(element.querySelectorAll('.btn-resolve').length).toBe(1);
    expect(Array.from(element.querySelectorAll('button'))
      .filter((button) => button.textContent === 'View Details').length).toBe(3);
  });

  it('requires a non-blank resolution message', async () => {
    await openResolve();
    expect(element.querySelector('[role="dialog"]')?.textContent).toContain('TKT-TEST');
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    enterResolution('   ');
    submitResolution();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('A resolution message is required.');
    http.expectNone((r) => r.url === '/api/tickets/resolve');
  });

  it('submits the trimmed message once, prevents dismissal while saving, and refreshes the page', async () => {
    await openResolve();
    enterResolution('  VPN access restored.  ');
    submitResolution();
    const resolve = http.expectOne((r) => r.url === '/api/tickets/resolve');
    expect(resolve.request.params.get('ticketID')).toBe('42');
    expect(resolve.request.params.get('summary')).toBe('VPN access restored.');
    await fixture.whenStable();
    expect(element.querySelector<HTMLTextAreaElement>('textarea')!.disabled).toBe(true);
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    expect(element.textContent).toContain('Resolving...');

    element.querySelector<HTMLElement>('.overlay')!.click();
    element.ownerDocument.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    submitResolution();
    http.expectNone((r) => r.url === '/api/tickets/resolve');
    expect(element.querySelector('[role="dialog"]')).not.toBeNull();

    resolve.flush({ ...ticket, ticketStatus: 'RESOLVED', resolutionSummary: 'VPN access restored.' });
    fixture.detectChanges();
    request().flush(result([{ ...ticket, ticketStatus: 'RESOLVED' }]));
    fixture.detectChanges();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
    expect(element.querySelector('.status-resolved')?.textContent).toBe('Resolved');
    expect(element.querySelector('.btn-resolve')).toBeNull();
  });

  it('keeps the message after an error and allows retrying', async () => {
    await openResolve();
    enterResolution('VPN access restored.');
    submitResolution();
    http.expectOne((r) => r.url === '/api/tickets/resolve')
      .flush({ message: 'Could not save resolution.' }, { status: 500, statusText: 'Server error' });
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not save resolution.');
    expect(element.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('VPN access restored.');
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);

    submitResolution();
    http.expectOne((r) => r.url === '/api/tickets/resolve').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Cannot reach the server.');
  });

  it('dismisses with Escape or Cancel and resets the message when reopened', async () => {
    await openResolve();
    enterResolution('Draft');
    element.ownerDocument.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
    element.querySelector<HTMLButtonElement>('.btn-resolve')!.click();
    fixture.detectChanges();
    expect(element.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('');
    element.querySelector<HTMLButtonElement>('.dialog button[type="button"]')!.click();
    fixture.detectChanges();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
    http.expectNone((r) => r.url === '/api/tickets/resolve');
  });
});
