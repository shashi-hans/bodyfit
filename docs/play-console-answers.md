# Play Console answers

Every answer below follows from the code, with the file that proves it. Transcribe them
into the console; nothing here needs judgement on the day.

Play's forms change. Treat this as the source of truth for *what the app does*, and check
the console for the current wording of each question.

## Data safety

Play defines "collect" as transmitting data off the device, and "share" as passing it to a
third party. Body Fit does neither, so almost every answer is no.

| Question | Answer | Why |
| --- | --- | --- |
| Does your app collect or share any of the required user data types? | **No** | No internet permission is declared, so transmission is impossible (`AndroidManifest.xml`) |
| Is all user data encrypted in transit? | **N/A** | Nothing is transmitted |
| Do you provide a way for users to request data deletion? | **Yes** | Uninstalling removes everything; there is no account and no server copy |
| Does your app collect data from children? | **No** | It collects nothing from anyone |
| Does your app use the Health Connect API? | **No** | No Health Connect dependency |

If the console insists on listing data types because the app is in Health & Fitness, the
honest position is: health and fitness data is **processed on the device and not collected**,
because it is never transmitted.

Supporting facts, each checkable in the repo:

- No `INTERNET` permission.
- No HTTP client, no analytics SDK, no crash reporter. Dependencies are Compose, Room,
  DataStore and Lifecycle only (`app/build.gradle.kts`).
- `android:allowBackup="false"`, so Android's own backup does not copy health data to the
  user's Google account either.
- Export and restore write and read a file the user picks through the system picker. The
  app never sees a path it was not handed.

## Permissions declaration

| Permission | Justification |
| --- | --- |
| `ACTIVITY_RECOGNITION` | Required by Android to read `TYPE_STEP_COUNTER`. The app's entire purpose is counting steps. |
| `POST_NOTIFICATIONS` | The lock-screen card showing daily totals, and the foreground service notification Android requires. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` | Counting steps while the app is closed. The `health` type is the correct one: the service reads a health sensor. |
| `RECEIVE_BOOT_COMPLETED` | Resuming step counting after a restart. |

## Foreground service declaration

Type: **health**

What it does: reads the step counter continuously and writes the totals to the device's
local database, so step, calorie and activity tracking continues while the app is closed.

Why a foreground service is necessary: Android does not permit background sensor access
without one. A background job or `WorkManager` cannot do this, because step intensity is
scored from 60-second windows measured as the user walks. Deferred or batched execution
loses the timing that calories, move minutes and heart points depend on.

User-facing control: a switch on the lock screen card page turns the service and its
notification off together.

## Health apps declaration

- The app is a personal fitness tracker, not a medical device.
- It provides no diagnosis, no treatment, and no clinical decision support.
- The wellbeing score and BMI are labelled in the app as indicative only, not a medical
  assessment and not an underwriting input.
- No data is transmitted, so there is no processing by any third party.

## Store listing

- **Category**: Health & Fitness
- **Privacy policy URL**: the hosted copy of `PRIVACY.md` (see below)
- **Contains ads**: No
- **In-app purchases**: No
- **Target audience**: adults; not directed at children

## Hosting the privacy policy

Play requires a publicly reachable URL, not a file in a repo. The cheapest route with this
repo already on GitHub:

1. Repo → Settings → Pages → Source: deploy from branch, `main`, `/` root.
2. The policy is then at `https://shashi-hans.github.io/bodyfit/PRIVACY` once `PRIVACY.md`
   is on `main`.
3. Paste that URL into the console and into the store listing.

Any host works. The requirement is only that it is public, stable, and reachable without a
login.

## Still needed before upload

- A release keystore and its password. See `docs/release-signing.md`.
- Screenshots, a 512x512 icon and a feature graphic.
- Confirmation that API 36 meets the current target-API requirement.
