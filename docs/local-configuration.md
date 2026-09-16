# Local configuration for Milestone 3A

This document describes configuration that stays on the owner's machine. Templates are committed; actual values are not.

## Files

| File | Purpose | Git status |
| --- | --- | --- |
| `backend/.env.example` | Safe backend template. | Tracked. |
| `backend/.env` | Actual backend configuration. | Ignored. |
| `apps/android/justdump.local.properties.example` | Safe Android template. | Tracked. |
| `apps/android/justdump.local.properties` | Actual Android configuration. | Ignored. |

Create each actual file by copying its adjacent `.example` file. Do not put values in source files, Gradle files, screenshots, terminal commands, or chat.

## Required values

### `JUSTDUMP_SUPABASE_URL`

In the Supabase Dashboard, open the JustDump development project and go to **Project Settings -> API**. Copy **Project URL** into both local files.

### `JUSTDUMP_SUPABASE_PUBLISHABLE_KEY`

On the same **Project Settings -> API** page, copy the **Publishable key** into both local files. This key identifies the project to clients; it is not a privileged secret. The Android client will use it only with the signed-in owner's session.

### `JUSTDUMP_OWNER_ID`

In **Authentication -> Users**, open the one owner account and copy its user UUID into `backend/.env` only. This is the server-side single-owner allow-list. It is not needed in the APK.

### `JUSTDUMP_API_BASE_URL`

Leave this blank until the FastAPI endpoint is running. Put the endpoint base URL in the Android local properties file only. The verified local phone workflow uses an ADB reverse rule and a debug-only loopback URL; it is not a LAN or deployed endpoint. A future hosted endpoint must use HTTPS.

## Deliberately not requested

Do not enter or share a database password, connection string, Supabase `service_role`/secret key, SMTP password, personal note content, or access token. The Milestone 3A FastAPI runtime verifies a signed owner JWT and calls Supabase PostgREST with that same JWT plus the publishable key. Postgres Row Level Security therefore applies to API reads and writes instead of being bypassed by a privileged backend credential.

The database schema migration is applied in the Supabase SQL Editor by the owner. It does not require a runtime database credential in this repository.
