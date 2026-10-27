import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
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
    component.form.setValue({ email: 'agent@example.com', password: 'password123' });
    component.submit();

    expect(createSupportAgent).toHaveBeenCalledWith('agent@example.com', 'password123');
    expect(navigate).toHaveBeenCalledWith(['/users'], { queryParams: { list: 'agents' } });
  });
});
