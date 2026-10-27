import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { NewSupportAgentComponent } from './new-support-agent.component';
import { UserDirectoryService } from './user-directory.service';

describe('NewSupportAgentComponent', () => {
  let fixture: ComponentFixture<NewSupportAgentComponent>;
  const createSupportAgent = vi.fn();

  beforeEach(async () => {
    createSupportAgent.mockReset();
    await TestBed.configureTestingModule({
      imports: [NewSupportAgentComponent],
      providers: [provideRouter([]), { provide: UserDirectoryService, useValue: { createSupportAgent } }],
    }).compileComponents();
    fixture = TestBed.createComponent(NewSupportAgentComponent);
    fixture.detectChanges();
  });

  it('validates form data and creates the agent before returning to the agents tab', () => {
    const component = fixture.componentInstance as any;
    component.submit();
    expect(createSupportAgent).not.toHaveBeenCalled();

    createSupportAgent.mockReturnValue(of({}));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.form.setValue({ email: 'agent@example.com', password: 'password123', confirmPassword: 'password123' });
    component.submit();

    expect(createSupportAgent).toHaveBeenCalledWith('agent@example.com', 'password123');
    expect(navigate).toHaveBeenCalledWith(['/users'], { queryParams: { list: 'agents' } });
  });

  it('requires matching passwords and retains the form when account creation fails', () => {
    const component = fixture.componentInstance as any;
    const element: HTMLElement = fixture.nativeElement;
    component.form.setValue({ email: 'agent@example.com', password: 'password123', confirmPassword: 'different123' });
    component.submit();
    fixture.detectChanges();
    expect(createSupportAgent).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Passwords do not match.');
    expect(element.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(true);

    const response = new Subject();
    createSupportAgent.mockReturnValue(response);
    component.form.controls.confirmPassword.setValue('password123');
    component.submit();
    component.submit();
    fixture.detectChanges();
    expect(createSupportAgent).toHaveBeenCalledTimes(1);
    expect(element.textContent).toContain('Creating...');
    response.error({ status: 409, error: { message: 'This email already has an account.' } });
    fixture.detectChanges();
    expect(element.querySelector('[role=alert]')?.textContent).toContain('This email already has an account.');
    expect(component.form.controls.email.value).toBe('agent@example.com');
    expect(element.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);
    expect(element.querySelector<HTMLAnchorElement>('.account-link a')?.getAttribute('href')).toBe('/users?list=agents');
  });
});
