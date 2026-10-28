import { APIRequestContext } from '@playwright/test';

export class CommentPage {

  constructor(
    private readonly request: APIRequestContext
  ) {}

  async getComments(
    token: string,
    ticketId: number
  ) {

    return this.request.get(
      `/comments/${ticketId}`,
      {
        headers: {
          Authorization:
            `Bearer ${token}`
        }
      }
    );
  }
}