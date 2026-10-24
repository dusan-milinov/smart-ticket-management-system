import { Component, input, output } from '@angular/core';

@Component({
  imports: [],
  selector: 'app-pagination',
  styleUrl: './pagination.component.css',
  templateUrl: './pagination.component.html',
})
export class PaginationComponent {
  // Zero-based, like the backend's page index.
  readonly page = input(0);
  readonly totalPages = input(0);
  readonly pageChange = output<number>();
}
