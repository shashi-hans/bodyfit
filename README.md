# Body Fit

An Android activity and hydration tracker. Steps, calories, distance, move minutes,
heart points and water, with daily goals, weekly targets, a weekly trend chart, and a
lock-screen card that stays current in the background.

Everything is computed and stored on the phone. There is no account, no server, no
network client and no analytics in the app.

## Screens

| Tab | What it holds |
| --- | --- |
| 🏠 Today | App name and greeting across the top, then one heart per metric (steps, calories, heart points), each filling from the bottom as its goal is approached, with the value and name under each, then water beside its add buttons, one box holding distance, move minutes, BMI and wellbeing, and a box-breathing timer |
| 💧 Water | Fill-level glass, quick-add sizes, today's log with per-entry undo |
| 📈 Trends | One metric at a time over Day, Week or Month. Day draws the 24 hours of today; Week is the calendar week starting Monday and Month the calendar month, both a bar per day. Each span carries its own target, average, best slot and a table of the same numbers. Tapping a bar in Week or Month opens that day hour by hour |
| 🩺 Health | BMI with its band, the wellbeing score with its rating, and the arithmetic that produced it line by line |
| 🎯 Goals | Daily goals for steps, calories, water, heart points and move minutes, a Recommendation button opening the suggested set, and weekly targets |

Everything that is not a goal sits behind the menu on the Today screen: About you, default
cup size, lock screen card, how the numbers work, backup, and about. Each is a page with a
back arrow, so no subject has two homes.

## Lock screen

Android does not let an app draw on the keyguard. The card is a foreground-service
notification at `VISIBILITY_PUBLIC`, so it shows steps, calories and water on the lock
screen, and its `+250 ml` / `+500 ml` buttons log water from there. It redraws whenever
today's row changes, including water logged inside the app.

Two details decide whether it actually appears, both learned on a HyperOS device:

- The channel sits at `IMPORTANCE_DEFAULT` with its sound and vibration removed, not at
  `IMPORTANCE_LOW`. Xiaomi HyperOS, and Android's own "hide silent notifications"
  setting, keep low-importance cards off the lock screen. `setSilent(true)` is also
  avoided for the same reason: it groups the card under "silent".
- There is no progress bar. A collapsed card gives the bar the same row as the text, and
  the calorie and water numbers are worth more there than the bar. The percentage is in
  the title instead.

Channel importance cannot be raised after a channel exists, so the id is
`activity_visible` and the original `activity` channel is deleted on start.

The tracker switch on the lock screen card page starts and stops that service. Android
requires a visible notification for a service that reads sensors in the background, so the
switch covers both the counting and the card. The same page reports whether Android is
restricting the app in the background, which is what silently stops counting, with a
shortcut to the setting.

## How the numbers are calculated

Steps come from the phone's own `TYPE_STEP_COUNTER` sensor. That counter reports a
running total since boot, so the service banks the difference between readings and
treats a reading lower than the last one as a reboot.

That sensor is optional in Android. A phone without one falls back to `SoftwarePedometer`,
which counts steps from the accelerometer: it takes the magnitude of the acceleration
vector, so the count does not depend on how the phone is carried, removes gravity with a
running mean, and counts upward crossings of a threshold that follows the recent size of
the bounce. Its steps are banked through the same path, so windows, cadence, calories and
hourly rows behave identically whichever source is running. Accuracy is lower and it costs
more battery, so it is never preferred over a step counter the phone already has; samples
are batched at one second to keep the CPU asleep between them.

`uses-feature` is declared `required="false"`, so the listing stays downloadable on every
device. Only a phone with neither sensor gets a warning it cannot dismiss, and the app
closes rather than opening: a screen of zeros would read as a bad day rather than as
missing hardware. The tracker service refuses to start for the same reason, which also
covers the boot path.

Steps are also bucketed into 60-second windows. A window opens on the first step after
a rest, not on the clock, so a walk that starts at 10:00:40 is measured to 10:01:40.
When a window closes, the next one waits for the next step, so standing still adds
nothing. A window is scored on the time it actually ran, not on an assumed 60 seconds:
the 5-second tick can only notice a window has expired up to 5 seconds late, and calling
65 seconds of steps a minute would report a pace nobody walked. The cadence of a closed
window decides what that minute was worth:

| Quantity | Rule |
| --- | --- |
| Distance | `steps x stride`, stride estimated as 41.5% of height |
| Move minute | any window at 10 or more steps a minute |
| Heart points | 1 per minute at 100+ steps/min, 2 at 130+ |
| Calories | `(MET - 1) x 3.5 x weight_kg / 200` per minute |

Heart points score effort from cadence because the phone has no heart-rate sensor. This
is the same fallback Google Fit uses. Move minutes count any walking however slow, so
pace changes what a minute is worth but not whether it happened. The 10-step floor is
what stops a single step across a room from earning a whole minute, since a window is
opened by a step in the first place.

### The MET model

MET comes from a curve through four anchors, not from bands:

| Steps a minute | MET |
| --- | --- |
| 10 | 1.40 |
| 60 | 2.11 |
| 100 | 3.0 |
| 130 | 6.0 |

Every anchor comes from the CADENCE-adults work, so the curve traces to one source rather
than mixing cadence research with compendium walking speeds.

100 and 130 are the published moderate and vigorous cadence thresholds, carrying the 3.0
and 6.0 MET they were defined against. Between them intensity rises about 1 MET per 10
steps a minute, which the interpolation reproduces: 110 gives 4.0 and 120 gives 5.0.

Below a breakpoint at 97.2 steps a minute the same work fits a much flatter line,
`METs = 1.2606 + 0.0141 x cadence`. The two lower anchors are that line at this app's
floor and at 60 steps a minute. Slow walking costs far less than walking is usually
credited with, and it matters here because the resting 1.0 is subtracted afterwards.

Anything between two anchors is interpolated, so walking faster always earns more and one
extra step never moves the rate by more than a fraction. Past 130 the rate holds flat: at
that pace a person is running, and the walking curve stops describing them.

Source: the CADENCE-adults studies (Tudor-Locke et al.), covering 21-40, 41-60 and 61-85
year olds, and the narrative review "How fast is fast enough?".

Cadence is first restated as the cadence a person of 170 cm would need to cover the same
ground, using the stride already derived from height. Energy tracks speed, and at 110
steps a minute someone 150 cm walks 4.1 km/h while someone 190 cm walks 5.2 km/h. Without
that correction the anchors would charge both the same.

The MET formula gives gross expenditure, which includes the 1.0 MET the body spends at
rest during the same minute. Subtracting it leaves activity burn alone, so the figure
answers "what did moving cost", not "what did I burn today". A window below the 10-step
floor is not movement, so it earns neither a move minute nor calories.

The rules live in `app/src/main/java/app/bodyfit/sensor/Metrics.kt` as
pure functions, covered by `app/src/test/java/app/bodyfit/sensor/MetricsTest.kt`.

### Hourly breakdown

Each slice of activity is written twice in one transaction: to the day's row and to an
`hourly_record` row for the hour the window started in. The Day span on Trends draws
those 24 hours directly, and tapping a bar in Week or Month opens the same breakdown for
that day. Water is not stored hourly, because each `water_entry` already carries the time
it was logged.

Hourly rows exist only from database version 3 onward, so days tracked before that show
totals with no breakdown, and the screen says so rather than drawing an empty chart.
Rows older than 90 days are pruned on the day rollover, which keeps the table at roughly
90 x 24 rows instead of growing for the life of the install.

## Chart colors

The Today card carries three hearts: steps green, calories yellow, heart points red. Each
fills from the bottom in proportion to its goal, with its value and name underneath.

Three nested bands round one outline came first and were dropped: a share of the goal is
read as a position along a curve, which is far harder than a fill level, and the bands
crowded together where the shape narrows to its point. The outline is the usual
`x = 16 sin^3 t` parametric, sampled into a path; its extent is computed from the samples
rather than assumed, because the lobes peak near y = 11.9 and a height guessed from the
formula's value at t = 0 clips them.
Water, distance and move minutes are neutral cards there, and take their own hue only
where they are the single colored thing on screen: the water glass on its own tab, and
the weekly chart, which draws one metric at a time.

Marks shown together must stay apart for colorblind readers, and yellow, red and green
are the hardest set for that. The dark steps are therefore not the light hues dimmed:
dark red is `#CC4444`, because the obvious `#E66767` lands 13.0 from the dark yellow in
normal vision, under the floor of 15. Which metric wears which of the three is free to
change: that is a permutation of the same set, so every pairwise separation is unchanged. The shipped set clears every gate in both modes,
with colorblind separation in the 6-8 band that is permitted only because the legend
names each arc with its value. Light-mode yellow also sits under 3:1 against the surface,
which the same labels cover.

These were checked with a palette validator, not by eye. The validator is not vendored
here, so if you change any of these values, re-check them against the gates it applied:

| Gate | Rule |
| --- | --- |
| Lightness band | OKLCH L within 0.43-0.77 light, 0.48-0.67 dark |
| Chroma floor | at least 0.1 |
| Colorblind separation | OKLab dE at least 8 between any two hues on screen together; 6-8 only with a text label beside each mark |
| Normal vision | OKLab dE at least 15 between any two such hues |
| Contrast | 3:1 against the surface, or the mark carries a visible label |

The shipped set: light `#EDA100` / `#E34948` / `#008300` on `#FCFCFB`, dark `#C98500` /
`#CC4444` / `#008300` on `#1A1A19`.

The palette and the rule are in
`app/src/main/java/app/bodyfit/ui/theme/Color.kt`.

## Derived numbers

The wellbeing score and the resting-burn estimate are computed from the day rows and the
profile every time they are drawn. Neither is stored, so neither can drift from the
activity behind it, and adding them needed no new table or column. They live in
`insights/Insights.kt` as pure functions of (days, settings, today), which is why they are
testable without a database or a clock.

| Number | Rule |
| --- | --- |
| Wellbeing score | 100 adjusted for BMI band, 14-day average steps, age and smoking |
| Recommended goals | Steps by age band (10,000 under 40, 8,500 to 59, 7,000 from 60); heart points and move minutes from the WHO's 150 moderate minutes a week; water at 35 ml/kg with an EFSA floor of 2.0 L for men and 1.6 L for women; calories from the recommended steps costed through the tracker's own MET model |
| Resting burn | Mifflin-St Jeor from weight, height, age and sex. Unspecified sex takes the midpoint of the two sex terms, wrong by about 83 kcal either way |

The Trends calorie view shows resting burn as a per-day figure beside active calories,
because the active number on its own reads as "all I burned today" when resting energy is
several times larger. The chart and the weekly target still count active calories only,
and the screen says so.

Goals has a "Recommended Goals for you" card under the daily goals, with one button that applies
all five. Height is deliberately not an input: it changes stride and so distance, but none
of these goals depend on it, and using it would give the numbers false authority.

The Health tab shows the score's working line by line, because a bare number invites more
trust than this heuristic deserves. The rating word is coloured green, amber or red, but
the word itself is always present: colour is a second encoding, never the only one. Those
three status colours are kept apart from the metric hues and are never reused as a data
series.

The wellbeing score is indicative. It is not a medical assessment and not an underwriting
decision, and the screen showing it says so. Age, sex and smoking are optional and stay on
the device.

## Backup

There is no sync, and Android's own auto-backup is off, so the export is the only way data
leaves or re-enters the phone. Both directions live under About on the Goals tab and go
through the system file picker, so no storage permission is involved and the app never
sees a path it was not handed.

The file carries everything the app shows: every day's steps, calories, move minutes,
heart points and water; the hourly breakdown behind the Day trend; every logged drink with
its timestamp; and height, weight, age, sex, smoking and all goals. BMI and the wellbeing
score are not stored, because both are computed from those inputs and would only go stale.

Restoring overwrites the days the file carries and leaves every other day alone, so an old
backup never erases newer tracking. Water entries and hourly rows for a restored day are
replaced rather than appended, so restoring the same file twice cannot double a total. The
tracker switch is not restored: whether this phone is counting is a property of the phone.

`format` is 2. A version 1 file still restores; it simply carries no hourly rows, and the
Day trend draws those days as empty. A file written by a newer format is refused outright
rather than half read.

## The current day

Screens read the day from one place in the view model rather than calling the clock
themselves. It re-anchors on the midnight boundary and again whenever the app returns to
the front, so leaving the app open overnight does not leave every screen showing
yesterday while the tracker writes to today. Composables take the date as an input, which
also lets their derived values be cached.

## Units

Water is stored in millilitres everywhere and only displayed differently: totals switch to
litres at a litre and above, with trailing zeros dropped, so a day reads "1.25 L" or "3 L"
instead of "1,250 ml". Add buttons and single log entries stay in millilitres, which is how
a glass is described. The rule is one object, `data/Volume.kt`, shared by the screens and
the lock-screen card, and covered by `VolumeTest`.

## Icons

Two drawables come from Ionicons by way of the Genie Icons gallery: the launcher mark
("body") and the steps glyph ("footsteps-ion"). Both are MIT licensed and credited in
NOTICE. Neither is fetched at runtime; `scripts/fit_icon.py` bakes the scale and translate
into plain path coordinates, and `scripts/render_icon.py` rasterises a drawable at 384, 96
and 48 px so an icon can be checked without installing a build.

Steps is the one metric with a real drawable rather than an emoji, because the same art has
to serve the notification's status-bar icon, where an emoji is not an option. Everything
else uses an emoji, and text-only places such as the notification body fall back to the
emoji for steps too.

## Permissions

| Permission | Why |
| --- | --- |
| `ACTIVITY_RECOGNITION` | read the step sensor (runtime, Android 10+) |
| `POST_NOTIFICATIONS` | show the lock-screen card (runtime, Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` | keep counting while the app is closed |
| `RECEIVE_BOOT_COMPLETED` | restart the tracker after a reboot |

No internet permission is declared.

## Data and privacy

Step counts, water entries, height and weight are personal health data. They are held in
a local Room database (`bodyfit.db`) and a local DataStore, and never leave the
device: no account, no sync, no third party, nothing to move out of India.

`android:allowBackup` is `false`. Android's auto-backup would otherwise copy this data to
the user's Google account, which is a third party and outside `ap-south-1`, and would make
the claim above untrue. The cost is that a reinstall starts empty, which is why the export
and restore in the app are the recovery route and are named as such on the Goals tab.

If cloud sync is added later it changes this picture completely: it would need DPDP Act
2023 consent, retention and deletion flows, and storage in `ap-south-1`.

## Verified on device

Redmi 22101320I, Android 14 (SDK 34), Qualcomm pedometer with both `step_counter` and
`step_detector`. Counted 7,429 steps, 58 move minutes, 50 heart points and 328 kcal over
one day, rolled over correctly at midnight into a fresh row, and logged water from both
the app chips and the lock-screen buttons.

Note for MIUI and HyperOS: those builds kill background services aggressively. If step
counts stall while the phone is idle, set Body Fit to **No restrictions** under
Settings → Apps → Body Fit → Battery saver, and lock it in Recents.

## Naming

The app is called **Body Fit**. The launcher icon is a white figure with its arms straight
out, outlined in mint, with "B" under its left hand and "F" under its right, on the brand
green (`res/drawable/ic_launcher_foreground.xml`).

The outline is not a stroke around a silhouette. Each limb is drawn twice on the same
geometry: mint at a wider stroke, then white at the real width. Mint is painted first, so
the white passes cover it wherever limbs overlap and only a 2-unit border survives. The
figure stays five editable lines instead of one hand-fitted outline. The outline has to be
mint, not the background green, or it would be invisible on the tile.

Three things were learned drawing it, and all three will bite anyone who edits it:

- A stroke of 5.5 or more on a small figure closes the gaps between limbs and it turns
  into a blob. Arms angled up meet the torso at an acute angle that fills in the same way,
  which is why the arms run straight out: perpendicular lines stay open where acute ones do
  not.
- Everything must stay within a radius of about 34 of the centre (54,54), because the
  adaptive-icon mask can crop to a 72dp circle. The bottom corners are the binding
  constraint, and round stroke caps buy real room: square caps on the hands push past the
  mask.
- The letters need roughly 2 units of clearance from the arm bar above and the legs
  beside them. Closing those gaps makes the white shapes merge at launcher size.

`scripts/render_icon.py` rasterises the drawable at 384, 96 and 48 px so the icon can be
checked without installing a build.

The wordmark on the Today screen reuses that same drawable and its background color, so the
mark inside the app is the mark on the home screen rather than a lookalike
(`ui/components/AppLogo.kt`). The name users see comes from `app_name` in
`res/values/strings.xml`; the package id is `app.bodyfit`.

Changing the package id makes Android treat the result as a different app. A device that
carries an older build keeps it installed alongside this one, with its own step and water
rows that this build cannot read.

## Build

Requires JDK 17 and an Android SDK with platform 34 and build-tools 34. Point
`ANDROID_HOME` at the SDK, or add `sdk.dir` to a local `local.properties` (gitignored).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # metric rules
./gradlew lintDebug
./gradlew installDebug         # with a device or emulator attached
```

The debug build uses the application id `app.bodyfit.debug`, so it can sit
alongside a release build.

Dependency versions are pinned. Lint reports newer androidx releases; those need a newer
AGP and compile SDK, so they are a deliberate upgrade, not a routine bump.

## Not built yet

- Reading from or writing to Health Connect, so data is shared with a watch or Fit
- Heart-rate-based heart points from a wearable
- Reminders to drink water
- History beyond the current week, and a month view
- Export of the local database
