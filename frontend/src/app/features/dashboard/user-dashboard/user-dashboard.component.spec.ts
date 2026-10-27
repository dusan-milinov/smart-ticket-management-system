import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UserDashboardComponent } from './user-dashboard.component';

describe('UserDashboardComponent ticket controls', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

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
    expect(root.querySelector('.total-number')?.textContent?.trim()).toBe('6');
    expect(root.textContent).toContain('No tickets match your filters.');
    expect(root.textContent).not.toContain('Create your first ticket');
    expect(root.querySelector('#recent-heading')?.textContent).toBe('Matching tickets');
  });
});
