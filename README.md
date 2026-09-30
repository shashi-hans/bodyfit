# Body Fit

An Android activity and hydration tracker. Steps, calories, distance, move minutes,
heart points and water, with daily goals, weekly targets, a weekly trend chart, and a
lock-screen card that stays current in the background.

Everything is computed and stored on the phone. There is no account, no server, no
network client and no analytics in the app.

## Screens

| Tab | What it holds |
| --- | --- |
| 🏠 Today | "Hi, <name>" and the greeting down the left of the top row beside the menu, the app mark and name on the right, then four nested arcs over the day's six figures, three to a row, each beside its icon and printed in its own colour, then the water add buttons, and a box holding BMI and wellbeing that opens their working |
| 💧 Water | Fill-level glass, quick-add sizes, today's log with per-entry undo |
| 🏋️ Exercise | Box breathing, running, cycling and skipping, and today's logged sessions |
| 📈 Trends | One metric at a time over Day, Week or Month, with the day's logged exercise under the chart. Day draws the 24 hours of today; Week is the calendar week starting Monday and Month the calendar month, both a bar per day. Each span carries its own target, average, best slot and a table of the same numbers. Arrows step back and forward a span at a time, back only as far as there is data. Tapping a bar in Week or Month opens that day hour by hour |
| 🎯 Goals | Daily goals for steps, calories, water, heart points and move minutes, a Recommendation button opening the suggested set, and weekly targets |

BMI and wellbeing open a page of their own from the Today screen, showing the score, the
band and the arithmetic line by line. It explains two standings rather than reporting the
day, so it is read occasionally and does not hold a place in the bar.

## First run

A fresh install opens on a setup screen rather than on the app, and the tracker does not
start until it is answered. Every figure the app reports is scaled by height or weight, so
opening straight into the tabs would show a full screen of numbers computed from untouched
defaults: they look like measurements of the user while being measurements of nobody.

Each measurement has to be moved before the button enables. A slider already sitting on a
plausible default cannot tell "this is my height" apart from "I did not read this screen".
Sex has no preselected chip for the same reason: "Prefer not to say" is an answer the user
picks, not a value they fail to change. The name is the one optional field, because nothing
is calculated from it.

The permission prompt waits for the answers too. Asking to read the step counter over a
screen that has not yet said why the app wants it is how a refusal is earned.

An install that predates this screen is not walled behind it. The flag is absent there, so
it falls back to whether a body measurement was ever written, which an existing user has
done through About you.

About you holds a name alongside the body measurements. It is optional, never leaves the
phone and is never part of a figure; the Today header greets "Hi, Guest" without one. The
field draws its own text once typing starts rather than the value read back from DataStore,
because a write completes after the next keystroke has arrived and a field fed by the stored
value receives characters out of order: "Shashi" lands as "ahS".

Everything that is not a goal sits behind the menu on the Today screen: About you, cup
sizes, lock screen card, how the numbers work, backup, and about. Each is a page with a
back arrow, so no subject has two homes. Cup sizes holds three picks (200, 250 and 500 ml by
default); they are the water buttons on Today and on the lock screen card, which Android caps
at three actions.

Water reminders are off until switched on, and repeat every 30 minutes to 3 hours. A
WorkManager job checks at that interval and posts a reminder with the three cup sizes as
buttons, but only inside the hours the user sets (08:00 to 22:00 by default, any hour from
12:00 am up to 11:59 pm; a start later than the end runs overnight, equal hours mean all day), only while the day's goal is not met,
and only when no drink was logged within the interval. Swiping a reminder away schedules it
to ring again in 15 minutes, and that check skips it when a drink was logged since the
dismissal. The swipe is heard through the notification's delete intent, which Android fires
only for a user dismissal, so a cup tap or the app cancelling it never snoozes.

The reminder's sound is picked through Android's own sound picker (notification sounds,
ringtones and alarms, plus Default and Silent), and a test button posts a reminder at once so
the choice can be heard. Android fixes a channel's sound when the channel is created, so each
sound gets its own channel id and the previous one is deleted; only one "Water reminders"
channel ever shows in the phone's settings. The sound URI is not carried in a backup, because
it names a file on this phone. By default the reminder keeps ringing until the user responds: the notification
carries `FLAG_INSISTENT`, so Android repeats the sound until a cup is tapped, the reminder is
swiped away or the shade is opened, and its channel is high importance so it drops down over
the screen with the cup buttons while it rings. A switch returns it to a single sound. Do Not
Disturb still silences it; ringing through that would need a full-screen alarm, which Play
limits to alarm and calling apps. WorkManager rather than exact alarms: a
reminder a few minutes late costs nothing, and exact alarms need a permission Play reviews.
The notification permission is asked when the switch is turned on. Tapping a cup logs the
drink and dismisses the reminder.

## Exercise

Box breathing, running, cycling and skipping, reached from a button on Today. Breathing is
a guided minute that records nothing. The other three run a timer and log a session.

A saved session writes an `exercise_session` row and folds its minutes, calories and heart
points into the day and into the hour it started, so the Today goals count exercise the step
sensor cannot see and the Day trend draws the run in the hour it happened. Without the hourly
half the day total and the 24 bars under it would add up to different numbers. The whole
session is billed to its starting hour rather than split across the hours it spanned: a
session is minutes, not hours, and splitting it would invent a precision the row does not
carry. Trends lists the day's sessions under the chart, so a bar no step count can explain
is readable.

Sessions are kept as rows as well as folded in because a calorie figure with no explanation
is not checkable: a user who sees 300 kcal appear should be able to find the ride that
caused it.
Removing a session takes its contribution back off the day.

| Activity | Produces steps | Effort |
| --- | --- | --- |
| Running | yes | GPS speed, else 8.0 assumed |
| Cycling | no | GPS speed, else 7.0 assumed |
| Skipping | no | jump rate from the accelerometer |

Calories use the same `(MET - 1)` basis as a walked minute, so a logged session and a
tracked one mean the same thing.

Skipping is the one activity whose intensity the phone can actually measure. A rope jump
is a large periodic bounce, which is the signal `SoftwarePedometer` already detects for
steps, so jumps are counted by the same peak detector on different bounds: at least 2.5
m/s^2 and no closer together than 280 ms, which caps the rate at about 210 a minute. The
measured rate picks a MET from the compendium's three paces, 8.8 at 80 jumps a minute,
11.8 at 110 and 12.3 at 140, interpolated between them.

Running and cycling are measured by GPS where the phone has a receiver and the user allows
it. Speed picks a MET from the compendium's speed bands: running anchored at 8, 10, 12 and
14 km/h, cycling at 16, 20, 25 and 30, interpolated between. Without a receiver, without
permission, or before 50 m has been covered, the assumed figure is used and the timer says
which is in force.

No coordinate is stored. Fixes are consumed for distance and dropped, and the session row
holds duration, distance and calories: how far and how fast, never where. A route trace is
a different category of data from a step count, and the app does not hold one. Pace is
derived from the stored distance and moving time rather than stored itself, so the two
cannot disagree. A session that measured nothing carries 0 metres, and its row prints no
distance at all rather than a misleading zero.

Fixes worse than 35 m of accuracy are ignored, and a hop implying more than 80 km/h is
treated as two bad fixes rather than a sprint. Location is requested when a running or
cycling session starts rather than at launch, so the reason is on screen when the prompt
appears, and a refusal starts the session anyway on the assumed effort.

The session is timed by `ExerciseSessionService`, a foreground service of type
`location|health`, not by the screen. A screen-owned timer lost while-in-use location the
moment the phone locked in a pocket, and the run came out short. The service holds the
clock, the jump counter and GPS for exactly as long as its "in progress" notification shows,
so the session also survives a rotation. A partial wake lock, bounded at six hours, keeps the clock and the accelerometer running with the screen off; the moving time is added up on the monotonic clock. The service also owns the session flag that pauses the step tracker's scoring: it sets the flag once it is running and clears it when it ends, and the step tracker clears any flag it finds with no live session behind it. `FOREGROUND_SERVICE_LOCATION` is an install-time
permission with no prompt; `ACCESS_BACKGROUND_LOCATION` is not requested.

Every session is paused automatically whenever the phone stops moving for three seconds,
and only time spent moving is billed. Waiting at a crossing is not exercise. Three seconds
rather than one because the top of a jump is briefly weightless and would otherwise read
as a stop.

### Scoring a window that outlived its minute

Every figure a window earns scales with the minutes it represents, not with the one minute
it was meant to be. That matters because a window does not always get closed on time. A
process the system freezes leaves one open for as long as the freeze lasts, and a service
restarted after a kill reads the whole gap out of the cumulative step counter and banks it
in a single go.

Awarding one move minute and at most two heart points per window, whatever its length, is
what made a frozen phone report a fraction of the walking it had counted. The steps came
back, because the hardware counter is cumulative; the minutes they were worth did not. A
Realme running Oplus's app-freezing framework showed 12,269 steps against 45 move minutes,
which is 273 steps a minute, while the same walk on a Xiaomi gave 146.

Past `MAX_HUMAN_CADENCE`, 220 steps a minute, the elapsed time is not believable: sustained
running sits near 180 and a sprinter's peak near 250. The duration is then inferred from the
steps at `RECOVERY_CADENCE`, a moderate 100 a minute. Those minutes and their energy cost
stand, but they earn no heart points, because a heart point is a claim about intensity and
intensity is exactly what was not observed.

The scoring lives in `Metrics.scoreWindow` rather than in the service, so it is a pure
function of steps, elapsed time and body measurements, and is tested against both failures.

While a session runs the tracker still counts steps but stops scoring its 60-second
windows. Without that a run would be billed twice, once through its steps and once through
the session. The flag lives in the tracker's DataStore rather than the database, because
the service reads it every five seconds and must not wait on a query to decide.

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

The Today card carries four nested half-circle arcs across the top and six figures
underneath, three to a row. The arcs are steps yellow, calories red, heart points green and
water blue; move minutes is cyan and distance violet, printed as figures with no arc, because
distance is steps counted a second way and move minutes track the same walking, so either
curve would retrace the steps arc. Cyan and violet replaced a green and a pink that sat next
to heart points and calories in the grid and read as the same measurement twice.

The well the arcs enclose holds the day's activity calories, which is the largest clear area
on the card and so carries the number worth reading first. Its info button sits above the
figure rather than under it: the well is read top down, and a button under the caption sat
closer to the figures below than to the number it belongs to.

The three columns of figures are aligned to the arc above them, the first flush left, the
last flush right and the middle centred, so the block reads as one shape with the arcs
instead of as a second grid stacked under them. The calorie figure in the grid is
the whole day's burn, activity plus the resting energy the day has accrued, prorated from
Mifflin-St Jeor across the hours elapsed. Two numbers rather than one because they answer
different questions: the goal is an activity target, and the total is what another tracker
would call "calories". Three round buttons open what a figure is made of: the total says what it counts and why the
goal is not scored against it, and the activity calories and heart points each break down
into walking and one line per logged session. The walking share is the day's total less the
sessions rather than a figure of its own, so the lines always add up to the number on the
card. The buttons are filled circles rather than bare glyphs, because a plain icon beside a
number reads as part of the label. Resting burn is prorated straight-line through the day; it is not
actually flat, being lower asleep and higher after a meal, but nothing here measures either. Each figure sits beside its own mark and is printed in its own colour,
so a figure and its arc pair without counting inwards from the outside. The figure is bare
and the line under it carries the unit: at this size "3,768 steps" costs the width a second
figure needs, and printing the unit twice buys nothing.

Half a circle rather than a whole one so the arcs take a band the width of the card instead
of a disc beside the figures, which leaves the figures the full width and room to stay large
at six of them. An arc's length is uniform along its sweep, so a given share of the goal is
always the same run of ink. The shapes tried before this could not manage that. A heart
traced by a band has an uneven perimeter, and emblems filling from the bottom have an uneven
area: a heart is narrow at its point and wide at its lobes, so filling half its height covers
well under half its ink, while a flame does the reverse. The same percentage looked different
on each metric. The icons stay, beside the figures, carrying identity without also being
asked to carry measurement.

Six hues at once is still past what colour alone can separate, even after the cyan and
violet. Identity therefore rests on the emoji and the number under each arc, and colour
carries nothing on its own.

The figures are printed in text-safe shades of the six hues rather than the hues themselves.
A colour that passes as a mark need not pass as a figure: measured against the card, the mark
colours give 1.90:1 for the yellow in light and 3.45:1 for the green in dark, well under the
4.5:1 body text needs. The text variants clear 5:1 in both themes.

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
| Wellbeing score | 100 adjusted for BMI band, average steps over the 13 full days before today (days before the first record left out), age and smoking |
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

Every figure the app shows is an estimate, produced on the phone from sensor readings and
published population averages rather than measured clinically. One wording says so, held in
`ui/components/Disclaimer.kt` and used by every screen that has to state where its numbers
stand: the app is a wellness tool, not a medical device, and nothing in it is intended to
diagnose, treat, cure or prevent any condition. Separate copies of that claim would drift,
and a page saying "estimate" beside one saying "measured" tells the user the app disagrees
with itself about its own accuracy.

The full statement carries on the pages with room for it, How the numbers work and About.
Shorter screens and dialogs carry a one-line form of the same claim. The wellbeing score
adds that it is neither a medical assessment nor an underwriting decision. Age, sex and
smoking are optional and stay on the device.

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

### Daily backup

The backup runs on a schedule from the moment the app is installed, with nothing to switch
on. A WorkManager job rewrites `Download/backup/bodyfit-backup.json` every day.

One file, never a second one, and never opened for writing in place. Each run writes into a
new pending row named `bodyfit-backup (writing).json`, and only once that is complete deletes
the old row and renames the new one. A full disk or a killed process mid-write therefore
leaves the previous backup whole, which matters because it is the only copy. The row is
remembered in the backup's own settings, and the liveness check on it is a query rather than
an open. After each write the app deletes any numbered copies it still owns in
`Download/backup`, matched by that exact folder so a folder such as `Download/backup-old` is
never touched. Android 9 and older write a temporary file beside the old one and rename it
over.

The one case beyond reach is a reinstall or a cleared app. Ownership of the old row is gone
with the old install, and reading another owner's row needs All files access, which Play
grants to file managers and little else. The app writes a fresh file and the previous one
stays until the user deletes it.

Daily rather than weekly because the file is the only copy: nothing syncs, so the gap between
the last backup and a lost phone is the history that is gone. One rewrite of one file a day
costs nothing measurable and cuts that gap from seven days to one. The cadence is not in the
filename, and was once: moving from weekly to daily then meant either a lie in the name or an
orphaned file on every phone.

That folder rather than the app's own is so the file survives an uninstall and a file
manager can copy it off the phone. Android 10 onwards an app cannot create a folder at the
root of shared storage, so the write goes through MediaStore's Downloads collection, which
needs no permission and no prompt. The cost of sitting outside the app is that any app granted storage access can read
it, and the file holds the whole history. The page says so.

"Choose a file" points the schedule anywhere else instead. The app takes persistable URI
permission on what the user picks, or the first scheduled run a day later fails with a
security error nobody is present to see.

One file is overwritten rather than a new one written each time, so a year does not leave
52 copies on a drive. The stream is opened in `wt` mode: without truncation a shorter
backup would leave the tail of the previous one behind and produce a file that is not valid
JSON. That file is still rewritten in place: a document picked through the system dialog
cannot be replaced atomically on every provider.

The job waits for the battery not to be low, so a write can land a few hours late. It is
re-asserted on every launch, because an app update or a force stop can drop the schedule
and a daily backup that quietly stopped is worse than one that never existed. A revoked
or deleted file is recorded and shown on the page rather than retried forever.

`format` is 3. Older files still restore: a version 1 file carries no hourly rows and a
version 2 file no exercise sessions, and the screens draw the missing detail as empty rather
than as a gap.

Sessions are carried as well as the day totals they were folded into, because a calorie
figure with no explanation is not checkable: a restored day showing 300 kcal should still be
able to name the ride that caused it. They are written back as rows only and never re-folded
into the day, since the day rows in the file already include them and folding again would
count every session twice.

A restore is also the second way through first-run setup. A backup carries the same height,
weight, age and sex the setup screen asks for, so a user moving from another phone answers
the questions by restoring rather than typing them again and hoping they match what the file
is about to overwrite. Only a restore that actually parsed opens the app. A file written by a newer format is refused outright
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
