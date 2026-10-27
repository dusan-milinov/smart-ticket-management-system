# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.0.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Page transitions

Client-side navigation uses Angular Router's native View Transitions integration
for a short fade and vertical slide between pages, including sign-in and
registration. The initial page load is not animated. The animations are disabled
when the operating system requests reduced motion. Browsers without the View
Transitions API continue to navigate normally, without animation.

## Profile

Open `/profile` to review your account and security settings. Avatar initials come
from non-empty segments of the email prefix. User activity uses
`GET /api/tickets/my/status-counts`; operator activity remains on the dashboard
and is not displayed or requested on the profile. Totals include
all statuses, and open tickets include `IN_QUEUE` and `FULFILLMENT`, matching the
dashboard. Failed activity requests show an error and a retry action.

**Change Password** opens a native modal dialog with focus containment, Escape
dismissal, and focus restoration to the trigger. Closing clears password values
and errors. During submission, dismissal and duplicate submissions are disabled.
The existing `POST /api/auth/change-password` flow invalidates sessions and returns
the user to login after success.

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
status and priority badges, column order, responsive mobile layout,
and dashboard page header. Status tabs show counts for the signed-in agent,
respecting search and priority but not the selected status or sort order.
Loading, empty, and error states are displayed, with a retry button on failure.

The page uses `GET /api/tickets/assigned?page=0&size=10` with the session cookie.
The backend derives the assignee from the authenticated account and restricts
the endpoint to support agents. The list includes all ticket statuses.
Counts use `GET /api/tickets/assigned/status-counts` with optional `priority` and
`search` parameters. A count failure is shown separately and can be retried
without losing the list or its filters.

After changing backend endpoints, rebuild and restart the backend as well as
refreshing the frontend. An older backend may silently ignore filter parameters
it does not recognize; reloading the browser alone cannot update the API.

Each row includes a green **Open** link to Ticket Details. Open tickets have a
**Resolve ticket** action on the details page, which opens a popup requiring
a non-blank resolution message. Submission calls
`PATCH /api/tickets/resolve` with `ticketID` and `summary` query parameters, then
refreshes the current page. Failed submissions keep the message for retry.
Resolved and cancelled tickets cannot be resolved again.

## Ticket display and list controls

My Tickets, Assigned Tickets, related tickets on account details, and the dashboard
ticket preview share status tabs, priority filtering, title/number search
(debounced by 300 ms), and newest/oldest sorting. Filters reset paginated lists to
the first page and apply on the server before pagination. Sorting uses `createdAt`
with `ticketId` as a stable tie-breaker. The dashboard continues to show at most
five matching tickets; its overview total is not a filtered count.

Ticket badges use the My Tickets palette everywhere, including Ticket Details
and reassignment dialogs: pending is amber, in progress blue, resolved green,
cancelled gray; low priority is teal, medium purple, and high red.
The API still uses `IN_QUEUE` and `FULFILLMENT`, but the UI displays **Pending**
and **In Progress**, including status changes in event history.

List endpoints accept optional `status`, `priority`, `search`, and Spring pageable
`sort` parameters. Assigned tickets always remain scoped to the current agent;
account-related tickets remain restricted to administrators.

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
