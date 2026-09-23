# Privacy policy

**Body Fit**

Last updated: 23 September 2026

## The short version

Body Fit does not collect your data. Everything it records stays on your phone. There is
no account, no server, no analytics, and the app has no permission to use the internet.

## What the app records

Body Fit stores the following on your device:

- **Steps**, read from your phone's step counter sensor, or counted from its accelerometer
  on phones that have no step counter.
- **Calories, distance, move minutes and heart points**, all calculated on your phone from
  those steps.
- **Water** you log yourself, with the time you logged it.
- **Height, weight, age, sex and whether you smoke**, if you enter them. These are optional
  and are used only to calculate BMI, stride length, calorie estimates and the indicative
  wellbeing score.
- **Your goals and app settings.**

## Where it is stored

In a database and a settings file inside the app's own private storage on your phone.
Android prevents other apps from reading them.

## What leaves your phone

Nothing.

The app declares no internet permission, so it is technically incapable of sending your
data anywhere. It contains no network code, no analytics library and no crash reporting.

Android's own automatic backup is switched off (`allowBackup="false"`), so your health data
is not copied to your Google account either.

## Sharing

Body Fit does not share your data with anyone. There are no third parties, no advertisers,
and no data processors, because no data ever leaves your device.

## Exporting your own data

The app can write everything it holds to a JSON file at a location you choose, and read
that file back. Those files are yours. Once you have exported one, where it goes and who
can read it is under your control, not the app's.

## Deleting your data

Uninstalling Body Fit deletes everything it holds. Because the data lives only on your
phone and is never transmitted, there is nothing held elsewhere to request deletion of, and
no account to close.

## Permissions and why each is needed

| Permission | Why |
| --- | --- |
| Physical activity (`ACTIVITY_RECOGNITION`) | Android requires it to read the step counter. Without it no steps can be counted. |
| Notifications (`POST_NOTIFICATIONS`) | To show the lock-screen card with your daily totals. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`) | To keep counting steps while the app is closed. Android requires a visible notification for this. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | To resume counting after you restart your phone. |

The app requests no location, no camera, no contacts, no storage and no internet access.

## Children

Body Fit is not directed at children and collects nothing from anyone, including children.

## Health information

The wellbeing score and BMI shown in the app are indicative only. They are not a medical
assessment, not a diagnosis, and not an input to any insurance or underwriting decision.
Consult a qualified professional about your health.

## Changes to this policy

If a future version of the app sends data anywhere, this policy will be updated before that
version is released, and the app will ask for your consent first.

## Contact

Raise an issue at <https://github.com/shashi-hans/bodyfit/issues>.
