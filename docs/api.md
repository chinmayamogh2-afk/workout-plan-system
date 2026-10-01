# API & Endpoint Reference – Workout Plan Management System

All endpoints are served by the embedded Tomcat on port `8080` (default).

## Authentication

The application uses Spring Security form-based login.

- Session cookie is issued after successful login.
- All endpoints except `/login` and `/actuator/health` require an authenticated session.
- CSRF tokens are required on all `POST` requests (automatically handled by Thymeleaf forms).

---

## Authentication Endpoints

### GET /login

Renders the login page.

**Access:** Public

**Response:** HTML — login form

---

### POST /login

Authenticates the user.

**Access:** Public

**Form parameters:**

| Parameter | Required | Description |
|---|---|---|
| `username` | Yes | Account username |
| `password` | Yes | Account password |

**On success:** Redirects to `/dashboard`

**On failure:** Redirects to `/login?error`

---

### GET /logout

Logs out the current user and invalidates the session.

**Access:** Authenticated

**Response:** Redirects to `/login?logout`

---

## Dashboard

### GET /dashboard

Displays the summary statistics dashboard.

**Access:** Authenticated (any role)

**Response:** HTML — dashboard with stat cards:
- Total plans
- Plans per status (DRAFT, SUBMITTED, APPROVED, PUBLISHED)
- Plans per difficulty (BEGINNER, INTERMEDIATE, ADVANCED)
- Recent plans table

---

## Workout Plan Endpoints

### GET /workouts

Lists workout plans with optional search and filter.

**Access:** Authenticated (any role)

| Role | Visible plans |
|---|---|
| TRAINER | Own plans only |
| ADMIN | All plans |

**Query parameters:**

| Parameter | Type | Description |
|---|---|---|
| `keyword` | String | Free-text search (name, description, muscle group) |
| `status` | Enum | Filter by status: `DRAFT`, `SUBMITTED`, `APPROVED`, `PUBLISHED` |
| `difficulty` | Enum | Filter by difficulty: `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |

**Response:** HTML — paginated plan list with filter form

---

### GET /workouts/new

Renders the create workout plan form.

**Access:** Authenticated (any role)

**Response:** HTML — empty plan form

---

### POST /workouts

Creates a new workout plan.

**Access:** Authenticated (any role)

**Form fields:**

| Field | Type | Validation | Description |
|---|---|---|---|
| `name` | String | Required, 3–150 chars | Plan name |
| `description` | String | Required, max 1000 chars | Plan description |
| `exerciseDetails` | String | Required | Exercise list / instructions |
| `durationMinutes` | Integer | Required, 1–600 | Duration in minutes |
| `difficulty` | Enum | Required | BEGINNER / INTERMEDIATE / ADVANCED |
| `targetMuscleGroup` | String | Required, max 100 chars | Targeted muscle group |
| `trainerName` | String | Required, max 100 chars | Trainer display name |

**On success:** Redirects to `/workouts/{id}` (detail page)

**On validation failure:** Re-renders the form with field errors

**Initial status:** Always `DRAFT`

---

### GET /workouts/{id}

Displays the detail view of a specific plan.

**Access:** Authenticated (any role); TRAINER can only view own plans

**Path parameter:** `id` — plan ID (Long)

**Response:** HTML — plan detail with status-action buttons

**Status buttons visible:**

| Current status | TRAINER sees | ADMIN sees |
|---|---|---|
| DRAFT | Submit button | Submit, Delete |
| SUBMITTED | (none) | Approve, Reject (→ DRAFT) |
| APPROVED | (none) | Publish |
| PUBLISHED | (none) | (none — terminal) |

---

### GET /workouts/{id}/edit

Renders the edit form pre-populated with existing plan data.

**Access:** Plan owner (TRAINER) or ADMIN

**Restrictions:**
- TRAINER cannot edit PUBLISHED plans
- Only the plan's creator can edit (unless ADMIN)

**Response:** HTML — populated plan form

---

### POST /workouts/{id}

Saves updates to an existing plan.

**Access:** Plan owner or ADMIN

**Form fields:** Same as POST /workouts

**On success:** Redirects to `/workouts/{id}`

**On validation failure:** Re-renders edit form with errors

---

### POST /workouts/{id}/delete

Deletes a workout plan.

**Access:** Plan owner or ADMIN

**Restrictions:** Only owner (any status) or ADMIN can delete

**On success:** Redirects to `/workouts`

---

### POST /workouts/{id}/status

Changes the status of a workout plan following the workflow rules.

**Access:** Role-dependent (see table below)

**Form parameter:**

| Parameter | Type | Description |
|---|---|---|
| `newStatus` | Enum | Target status |

**Allowed transitions:**

| From | To | Allowed roles |
|---|---|---|
| DRAFT | SUBMITTED | Plan owner (TRAINER or ADMIN) |
| SUBMITTED | APPROVED | ADMIN only |
| SUBMITTED | DRAFT | ADMIN only (reject) |
| APPROVED | PUBLISHED | ADMIN only |

**On invalid transition:** Redirects with error flash message

**On success:** Redirects to `/workouts/{id}`

---

## Actuator Endpoints

### GET /actuator/health

Returns the application health status.

**Access:** Public (no authentication required)

**Response body (JSON):**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

**Used by:** Docker `HEALTHCHECK`, Jenkins health-check stage, Ansible `uri` module

---

### GET /actuator/info

Returns application metadata.

**Access:** Public

**Response body (JSON):**

```json
{
  "app": {
    "name": "Workout Plan Management System",
    "version": "1.0.0"
  }
}
```

---

## Error Pages

| HTTP Status | URL | Description |
|---|---|---|
| 400 | /error (mapped) | Bad request / validation error |
| 404 | /error (mapped) | Resource not found |
| 500 | /error (mapped) | Internal server error |

Custom Thymeleaf templates: `templates/error/400.html`, `404.html`, `500.html`

---

## Default Test Accounts

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |
| `trainer` | `Trainer@123` | TRAINER |

Seeded by `DataInitializer` on first application startup.
