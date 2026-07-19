# CLAUDE.md
 
Project instructions for Claude Code when working in this repository.
 
## Project Overview
 
This is a monorepo with three services:
- `frontend/` — React + TypeScript + Vite + Tailwind CSS
- `backend/` — Spring Boot (Java 21), PostgreSQL
- `recommendation/` — Python, FastAPI

## Git & Commit Rules
 
- **Never run `git commit` or `git push` without my explicit approval.** Always stop after making changes and show me a summary of what changed (diff or file list) so I can review before anything is committed.
- Never use `git push --force` unless I explicitly ask for it in that exact message.
- Never amend or rewrite existing commit history unless I explicitly ask.
- When you believe changes are ready to commit, propose a commit message and wait for me to say "commit" or "yes" before running the command.
- Do not commit anything under `.claude/`, `.env`, `node_modules/`, `target/`, `__pycache__/`, or any other paths already listed in `.gitignore`.

## Code Change Rules
 
- Before making changes across multiple files, briefly explain the plan first and wait for confirmation if the change is large or touches more than ~3 files.
- Don't refactor or "clean up" code that wasn't part of the request, unless asked.
- Preserve existing naming conventions and folder structure — don't reorganize things unprompted.
- When adding a new dependency (npm package, Maven dependency, pip package), tell me what it is and why before installing it.

## Testing & Verification
 
- After scaffolding or modifying a service, always try to run/build it and confirm there are no errors before telling me it's done.
- For backend changes, prefer running `./gradlew test` if tests exist.
- For frontend changes, prefer running `npm run build` to catch type errors.
- Do not mark a task as complete if you haven't verified it actually runs.

## Style & Stack Conventions
 
- Frontend: functional React components with hooks, TypeScript strict mode, Tailwind utility classes (no inline styles unless unavoidable, e.g. dynamic background-image URLs).
- Backend: standard Spring Boot layering — controller → service → repository → entity/dto. Use Lombok to reduce boilerplate.
- Recommendation: keep FastAPI routes thin; business logic goes in separate modules under `models/` or a `services/` folder.
- Keep environment-specific config (DB passwords, API keys) out of code — use environment variables or `.env` (gitignored).

## Communication
 
- If a request is ambiguous, ask one clarifying question rather than guessing on something significant (e.g. database schema decisions, API contract between frontend and backend).
- Flag any security-sensitive code (auth, secrets handling, SQL queries) explicitly so I know to review it carefully.

