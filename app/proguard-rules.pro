# Room, WorkManager, DataStore and Compose ship their own consumer rules, so nothing else
# in the app needs keeping by name.

# Enum constants whose names are written to storage and read back by name: Sex in the
# settings file and in backups, ExerciseType in session rows and in backups. Renamed, a
# value written by one release would not be readable by the next.
-keepclassmembers enum app.bodyfit.data.Sex { <fields>; }
-keepclassmembers enum app.bodyfit.data.ExerciseType { <fields>; }
