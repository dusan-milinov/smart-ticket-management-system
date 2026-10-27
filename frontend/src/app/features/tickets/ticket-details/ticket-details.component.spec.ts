import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AuthService, CurrentUser } from '../../../core/auth.service';
import { Ticket } from '../models/ticket.model';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TicketDetailsComponent } from './ticket-details.component';

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

describe('TicketDetailsComponent', () => {
  let fixture: ComponentFixture<TicketDetailsComponent>;
  let http: HttpTestingController;
  let element: HTMLElement;
  let currentUser: ReturnType<typeof signal<CurrentUser | null>>;

  beforeEach(async () => {
    currentUser = signal({ email: 'user@example.com', role: 'ROLE_USER' });
    await TestBed.configureTestingModule({
      imports: [TicketDetailsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '42' }) } } },
        { provide: AuthService, useValue: { user: currentUser.asReadonly() } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketDetailsComponent);
    element = fixture.nativeElement;
  });

  afterEach(() => http.verify());

  function loadTicket(status = 'FULFILLMENT'): void {
    fixture.detectChanges();
    http.expectOne('/api/tickets/42').flush({ ...ticket, ticketStatus: status });
    fixture.detectChanges();
    http.expectOne('/api/ticket-events/42').flush([]);
    fixture.detectChanges();
    http.expectOne('/api/comments/42').flush([]);
    fixture.detectChanges();
  }

  it('offers cancellation to users and updates the ticket after cancellation', () => {
    loadTicket();

    expect(element.querySelector('.back')?.textContent).toContain('Back to my tickets');
    expect(element.querySelector('.ticket-actions')?.textContent).toContain('Cancel ticket');
    expect(element.querySelector('.ticket-actions')?.textContent).not.toContain('Resolve ticket');
    expect(element.querySelector('.ticket-header .ticket-actions')).not.toBeNull();
    expect(element.querySelectorAll('.ticket-actions').length).toBe(1);

    element.querySelector<HTMLButtonElement>('.ticket-actions button')!.click();
    fixture.detectChanges();
    expect(element.querySelector('[role="dialog"]')?.textContent).toContain('TKT-TEST');
    element.querySelector('.dialog')!.dispatchEvent(new Event('submit', { cancelable: true }));

    const request = http.expectOne('/api/tickets/cancel?ticketID=42');
    expect(request.request.method).toBe('PATCH');
    request.flush({ ...ticket, ticketStatus: 'CANCELLED' });
    fixture.detectChanges();
    http.expectOne('/api/ticket-events/42').flush([]);
    fixture.detectChanges();

    expect(element.querySelector('.status-cancelled')?.textContent).toBe('Cancelled');
    expect(element.querySelector('.ticket-actions')).toBeNull();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
  });

  it('offers resolution to agents and records the resolution message', async () => {
    currentUser.set({ email: 'agent@example.com', role: 'ROLE_SUPPORT_AGENT' });
    loadTicket();

    expect(element.querySelector('.back')?.textContent).toContain('Back to assigned tickets');
    expect(element.querySelector('.ticket-actions')?.textContent).toContain('Resolve ticket');
    expect(element.querySelector('.ticket-actions')?.textContent).not.toContain('Cancel ticket');
    expect(element.querySelector('.ticket-header .ticket-actions')).not.toBeNull();
    expect(element.querySelectorAll('.ticket-actions').length).toBe(1);

    element.querySelector<HTMLButtonElement>('.ticket-actions button')!.click();
    fixture.detectChanges();
    expect(element.querySelector('[role="dialog"]')).not.toBeNull();
    const textarea = element.querySelector<HTMLTextAreaElement>('.dialog textarea')!;
    textarea.value = '  VPN access restored.  ';
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(element.querySelector<HTMLButtonElement>('.dialog button[type="submit"]')!.disabled).toBe(false);
    element.querySelector('.dialog')!.dispatchEvent(new Event('submit', { cancelable: true }));

    const request = http.expectOne((r) => r.url === '/api/tickets/resolve');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.params.get('ticketID')).toBe('42');
    expect(request.request.params.get('summary')).toBe('VPN access restored.');
    request.flush({ ...ticket, ticketStatus: 'RESOLVED', resolutionSummary: 'VPN access restored.' });
    fixture.detectChanges();
    http.expectOne('/api/ticket-events/42').flush([]);
    fixture.detectChanges();

    expect(element.querySelector('.status-resolved')?.textContent).toBe('Resolved');
    expect(element.querySelector('.ticket-actions')).toBeNull();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
  });

  it('does not offer actions for terminal tickets', () => {
    loadTicket('RESOLVED');
    expect(element.querySelector('.ticket-actions')).toBeNull();
  });

  it('renders comments directly after Event History', () => {
    loadTicket();
    expect(element.querySelector('.history')?.nextElementSibling?.tagName).toBe('APP-TICKET-COMMENTS');
    expect(element.querySelector('app-ticket-comments')?.textContent).toContain('No comments yet.');
  });

  it('uses friendly status labels in badges and status change history', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/42').flush(ticket);
    fixture.detectChanges();
    http.expectOne('/api/ticket-events/42').flush([{
      id: 1, eventType: 'STATUS_CHANGED', oldValue: 'IN_QUEUE', newValue: 'FULFILLMENT',
      createdAt: '2026-10-26T10:00:00Z',
    }]);
    http.expectOne('/api/comments/42').flush([]);
    fixture.detectChanges();
    expect(element.querySelector('.status-fulfillment')?.textContent).toBe('In Progress');
    expect(element.textContent).toContain('Pending → In Progress');
    expect(element.textContent).not.toContain('In queue');
    expect(element.textContent).not.toContain('Fulfillment');
  });

  it('does not request comments when loading the ticket fails', () => {
    fixture.detectChanges();
    http.expectOne('/api/tickets/42').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(element.querySelector('app-ticket-comments')).toBeNull();
    http.expectNone('/api/comments/42');
  });
});
