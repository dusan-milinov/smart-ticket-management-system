import { Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  styleUrl: './page-header.component.css',
  templateUrl: './page-header.component.html',
})
export class PageHeaderComponent {
  readonly heading = input.required<string>();
  readonly description = input('');
}
