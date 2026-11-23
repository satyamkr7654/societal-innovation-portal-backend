# API reference

All addresses start with `/api`. Bodies are JSON unless noted.

## Calling it

1. `GET /auth/me` sets the `XSRF-TOKEN` cookie (204 when signed out).
2. Send that value as the `X-XSRF-TOKEN` header with every POST.
3. `POST /auth/login` sets the `sip_session` cookie; browsers then send it automatically.

Errors look like `{"message": "..."}` and the message can be shown to the user.

| Status | Meaning |
|---|---|
| 400 | Something in the request is not valid |
| 401 | Not signed in, or wrong email or password |
| 403 | Not your role, account not approved, or missing CSRF token |
| 404 | Does not exist, or you may not see it |
| 409 | Not possible in the current state, or someone changed it first |
| 413 | Photo over 5 MB |
| 429 | Too many failed sign-ins |

## Accounts

| Call | Body | Answer |
|---|---|---|
| POST /auth/register | `name, email, password, role` (`citizen`, `university`, `industry`), `phone` for citizens, `organizationName` for the others | 201 user |
| POST /auth/login | `email, password` | 200 user |
| POST /auth/logout | none | 204; ends all sessions of this user |
| GET /auth/me | | 200 user or 204 |

Passwords: 8 to 72 characters with at least one number.

## Problems

| Call | Who | Body |
|---|---|---|
| GET /problems | signed in; unverified and rejected ones only for their author and admins | |
| GET /problems/{caseId} | as above | |
| POST /problems | citizen | multipart: `problem` (JSON below) and optional `photo` (JPG or PNG, 5 MB) |
| POST /problems/{caseId}/feedback | author, once solved | `rating` 1 to 5, `remarks`, `verifiedOnGround` |
| GET /problems/similar?category&lat&lng | signed in | open, same category, within 500 m |
| GET /problems/{caseId}/project | signed in; 204 when no university is assigned | |
| GET /photos/{name} | signed in | |
| GET /public/problems | anyone | |

```json
{
  "title": "Broken water pipe near school",
  "description": "Water has been leaking here for many days.",
  "category": "Water",
  "district": "Ranchi",
  "location": { "address": "Harmu", "lat": 23.35, "lng": 85.31 }
}
```

## Notifications (own only)

GET /notifications · GET /notifications/unread-count · POST /notifications/{id}/read · POST /notifications/read-all

## Admin

| Call | Body |
|---|---|
| GET /admin/stats, /admin/universities, /admin/accounts/pending | |
| POST /admin/accounts/{id}/approve, /reject | none |
| POST /admin/problems/{caseId}/validate | `remarks` optional |
| POST /admin/problems/{caseId}/reject | `reason` |
| POST /admin/problems/{caseId}/assign | `organizationId`, `remarks` optional |
| POST /admin/problems/{caseId}/proposal/approve | `remarks` optional |
| POST /admin/problems/{caseId}/proposal/request-changes | `reason` |
| POST /admin/problems/{caseId}/complete | `remarks` optional |
| POST /admin/problems/{caseId}/return | `reason` |

## University (assigned university only)

| Call | Body |
|---|---|
| GET /university/assignments | |
| POST /university/problems/{caseId}/accept | `department, facultyLead, studentsCount` (1 to 100) |
| POST /university/problems/{caseId}/decline | `reason` |
| POST /university/problems/{caseId}/proposal | `summary` and `approach` (50+ characters), `durationWeeks` (1 to 104), `items` [`label`, `amount`] (1 to 20) |
| POST /university/problems/{caseId}/milestones/{id}/complete | `note` |
| POST /university/problems/{caseId}/request-completion | none |
| POST /university/problems/{caseId}/pledges/{id}/accept | none |
| POST /university/problems/{caseId}/pledges/{id}/decline | `reason` |

## Industry

| Call | Body |
|---|---|
| GET /industry/proposals, /industry/pledges | |
| POST /industry/problems/{caseId}/pledges | `type` (FUNDING, EQUIPMENT, MENTORSHIP), `amount` for funding, `description` |
| POST /industry/problems/{caseId}/pledges/{id}/withdraw | none |
