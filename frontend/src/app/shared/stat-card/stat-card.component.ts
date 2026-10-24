import { Component, input } from '@angular/core';

@Component({
  selector: 'app-stat-card',
  styleUrl: './stat-card.component.css',
  templateUrl: './stat-card.component.html',
})
export class StatCardComponent {
  readonly label = input.required<string>();
  // Placeholder until real data is wired in.
  readonly value = input('—');
}
