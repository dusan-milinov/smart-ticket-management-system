GitHub Copilot Instructions
Project Context
•	Smart Ticket Management System
•	Frontend: Angular, TypeScript, HTML, CSS
•	Backend: Spring Boot, Java, MySQL
•	Authentication: JWT with HttpOnly cookies
•	Follow existing project architecture and conventions.
Efficiency
•	Minimize unnecessary context and tool calls.
•	Inspect only files relevant to the current task.
•	Do not scan the entire repository unless necessary.
•	Keep responses short and focused.
•	Avoid repeating explanations.
•	Prefer small, incremental changes.
Code Changes
•	Modify only files necessary for the requested task.
•	Do not refactor unrelated code.
•	Reuse existing services, models and shared components.
•	Do not introduce new dependencies without permission.
•	Do not change backend code for frontend-only tasks.
•	Preserve existing functionality.
•	Prefer simple, readable and maintainable solutions.
Angular
•	Follow existing standalone component conventions.
•	Prefer signals and computed signals where appropriate.
•	Use modern Angular template syntax.
•	Reuse existing services for API requests.
•	Avoid duplicating business logic.
•	Keep HTML, CSS and TypeScript responsibilities separate.
Testing
•	Do not generate new test files unless explicitly requested.
•	Do not run the entire test suite unless necessary.
•	Do not skip essential validation when a change requires it.
Communication
•	Do not provide lengthy explanations.
•	Summarize changes in 2–3 sentences.
•	Mention errors or risks briefly.
•	Ask before making significant architectural changes.
Important
Prioritize minimal, focused changes while maintaining code quality and correctness.
