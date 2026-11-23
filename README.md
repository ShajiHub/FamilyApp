# FamilyApp

FamilyApp is an Android app that helps a family organise everyday life in one place: events, meals, routines, to-dos, checklists and photos.

It is written in Java using classic Android Views, with one screen (Activity) per feature.

## Features

| Feature | Screen | What it does |
|---|---|---|
| **Calendar / Events** | `AddEvents` | Shows today's date and opens the phone's calendar app with a new one-hour event ready to fill in. |
| **Monthly Schedule** | `viewCalendar` | Pick any day on the calendar and save plans for it. Shows how many plans are in that month. Long-press a plan to delete it. |
| **Meal Planner** | `MealPlanner` | Spin the wheel to pick today's school lunch, or choose from the menu (Chicken wrap, Burger, Shawarma, Poutine, Mac and Cheese, Pancake, Sushi, Pizza). The chosen lunch is remembered for the day. |
| **Timer** | `FamAppTimer` | Countdown timers for family routines: **Get Ready** (15 min), **Screen Time** (60 min) and **Clean Up** (30 min), with start, pause and reset. Plays a sound when time is up and keeps the screen on while running. |
| **To-do List** | `ToDoList` | Add tasks; tap a task to tick it off and it disappears after a second. Tasks are saved. |
| **Checklist** | `CheckList` | Packing lists for **Winter**, **Summer** and a **First-Aid Kit**. Ticks are saved. |
| **Capture Moments** | `PhotoGallery` | Takes full-size photos with the camera app and keeps them in a family gallery. Tap a photo to see it large; long-press to delete. |
| **Activities** | `ActivitiesFun` | Random indoor and outdoor activity ideas for kids, and a link to the Reading Log. |
| **Reading Log** | `Reading` | Record books read and pages, with totals. Long-press an entry to delete it. |
| **Contact Us** | `ContactUs` | Thank-you and credits screen with two image slideshows. |

All lists are saved on the phone, so they are still there after closing the app or rotating the screen. The app supports portrait and landscape layouts and uses Google Fonts such as Boogaloo, Atma, Amatic SC and Alegreya.

## Tech stack

- **Language:** Java 17
- **UI:** Android Views (XML layouts), ConstraintLayout, RecyclerView, ListView, Spinner, CalendarView
- **Libraries:** AndroidX AppCompat 1.7, Activity 1.9, Material Components 1.12, ConstraintLayout 2.2, RecyclerView 1.3, ExifInterface 1.3
- **Storage:** SharedPreferences with JSON (`FamilyStore`); photos in the app's private Pictures folder, shared with the camera through a `FileProvider`
- **Build:** Gradle 8.11.1, Android Gradle Plugin 8.7.3
- **SDK:** minimum Android 5.0 (API 21), target and compile Android 15 (API 35)
- **Permissions:** none. The camera and calendar are opened through their own apps.
- **Tests:** Robolectric screen tests

## Project structure

```
FamilyApp/
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/shajitha/familyapp/
│       │   │   ├── MainActivity.java       # Home screen and navigation
│       │   │   ├── AddEvents.java          # Add an event to the phone's calendar
│       │   │   ├── viewCalendar.java       # Monthly Schedule: plans for each day
│       │   │   ├── MealPlanner.java        # Lunch wheel and menu
│       │   │   ├── FamAppTimer.java        # Routine countdown timers
│       │   │   ├── ToDoList.java           # To-do list
│       │   │   ├── CheckList.java          # Packing / first-aid checklists
│       │   │   ├── ChecklistAdapter.java   # RecyclerView adapter (header and item rows)
│       │   │   ├── ChecklistItems.java     # Checklist data model
│       │   │   ├── PhotoGallery.java       # Camera capture and photo gallery
│       │   │   ├── ActivitiesFun.java      # Kids' activity ideas
│       │   │   ├── Reading.java            # Reading log
│       │   │   ├── ContactUs.java          # Credits and slideshow
│       │   │   └── FamilyStore.java        # Saves lists on the phone
│       │   └── res/
│       │       ├── layout/  layout-land/   # Portrait and landscape screens
│       │       ├── drawable-nodpi/         # Photos and pictures (never rescaled)
│       │       ├── font/                   # Google Fonts
│       │       ├── values/                 # Strings, colours, themes, menus and ideas
│       │       └── xml/file_paths.xml      # Where photos are stored
│       └── test/                           # Robolectric screen tests
├── build.gradle
├── settings.gradle
└── gradle/wrapper/
```

## Getting started

1. Clone the repository:
   ```bash
   git clone https://github.com/ShajiHub/FamilyApp.git
   ```
2. Open the `FamilyApp` folder in Android Studio (Ladybug 2024.2 or newer).
3. Let Gradle sync, then run the `app` configuration on an emulator or device with Android 5.0 or later.

## Running the tests

The tests open every screen on a simulated phone, tap through it and rotate it:

```bash
./gradlew testDebugUnitTest
```

In Android Studio, right-click `app/src/test` and choose **Run 'Tests in test'**.

## Roadmap

Ideas for future versions:

- Family member profiles and tasks assigned to each person
- Chore chart with points and rewards
- Reminders and notifications for to-dos and plans
- Shared family sync across phones

## Author

Developed by [ShajiHub](https://github.com/ShajiHub).
