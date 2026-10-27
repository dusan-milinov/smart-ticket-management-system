# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.0.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Creating tickets

After signing in, open `/tickets/new` (also linked as **Create Ticket** in the user
navigation). The form submits to `POST /api/tickets/add` using the session cookie
and the configured development proxy.

All four fields are required: title, description, priority (`LOW`, `MEDIUM`, or
`HIGH`), and type (`INCIDENT` or `SOFTWARE_REQUEST`). The backend assigns the
creator, ticket number, and initial status. On success, the page displays the
ticket number and resets for another submission. Failed submissions preserve
the entered values and display an error so the user can retry.

## Assigned tickets

Support agents can open **Assigned Tickets** from the navigation at
`/tickets/assigned`. The page lists only tickets assigned to the signed-in agent,
newest first, with ten tickets per page. It shares the My Tickets table styles,
status and priority badges, responsive mobile layout, and dashboard page header.
Loading, empty, and error states are displayed, with a retry button on failure.

The page uses `GET /api/tickets/assigned?page=0&size=10` with the session cookie.
The backend derives the assignee from the authenticated account and restricts
the endpoint to support agents. The list includes all ticket statuses.

Each row includes a **View Details** placeholder (navigation is not implemented
yet). Open tickets also have a **Resolve Ticket** action, which opens a popup
requiring a non-blank resolution message. Submission calls
`PATCH /api/tickets/resolve` with `ticketID` and `summary` query parameters, then
refreshes the current page. Failed submissions keep the message for retry.
Resolved and cancelled tickets cannot be resolved again.

## Ticket comments

Ticket Details includes an expanded-by-default Comments card below Event History.
It loads `GET /api/comments/{ticketId}` and posts multiline text to
`POST /api/comments` with `{ ticketId, content }`, using the session cookie.
Comments are sorted oldest first. Failed posts retain the draft for retry.

Each comment response includes `authorEmail` and `authorRole` (`USER`,
`SUPPORT_AGENT` or `ADMIN`), which determine the author shown and the side of the
timeline. The application has no separate display name, so the email is shown.
User comments appear left in blue; support-agent comments appear right in orange
with an Operator label; admin comments appear right in orange with an Admin label.
Mobile uses one left-aligned timeline.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
