import { Component } from '@angular/core';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/stat-card/stat-card.component';

@Component({
  selector: 'app-support-agent-dashboard',
  imports: [PageHeaderComponent, StatCardComponent],
  templateUrl: './support-agent-dashboard.component.html',
})
export class SupportAgentDashboardComponent {}
