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
};

describe('AssignedTicketsComponent', () => {
  let fixture: ComponentFixture<AssignedTicketsComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignedTicketsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AssignedTicketsComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows submission time and a side-effect-free View link', () => {
    http.expectOne('/api/tickets/assigned?page=0&size=10').flush({
      content: [ticket], number: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Submitted');
    expect(root.textContent).toContain('Oct 26, 2026');
    const view = root.querySelector<HTMLAnchorElement>('.btn-open');
    expect(view?.textContent?.trim()).toBe('View');
    expect(view?.getAttribute('href')).toBe('/tickets/42');
    expect(http.match('/api/tickets/42/start')).toHaveLength(0);
  });
});
