import { Component } from '@angular/core';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/stat-card/stat-card.component';

@Component({
  selector: 'app-user-dashboard',
  imports: [PageHeaderComponent, StatCardComponent],
  templateUrl: './user-dashboard.component.html',
})
export class UserDashboardComponent {}
