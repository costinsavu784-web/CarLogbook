# Car Logbook (Android)

A free, personal car service logbook for Android. No account, no server, no ads. All data stays on your phone.

## What it does

- **Garage:** add several cars (name, make, model, year, odometer). The app reopens on the car you used last.
- **History:** every service for a car, newest first. Tap one to edit or delete it.
- **Add service:** what you did (with quick-pick suggestions), date, kilometres, notes, and an optional "repeat every X km / Y months".
- **Reminders:** a daily check (around 09:00) sends a notification when something is due soon or overdue. It also asks you to update your kilometres after 30 days, because the app cannot read the car's odometer.
- **Per-car warning time:** by default 1,000 km and 14 days before a service is due. You can change it in Edit car.

## How to run it (free)

1. Install **Android Studio** (free): https://developer.android.com/studio
2. In Android Studio choose **File > Open** and select this `CarLogbook` folder. Wait for the first Gradle sync (a few minutes, it downloads libraries).
   - If it offers to update the Android Gradle Plugin or Gradle, you can accept.
3. On your phone: Settings > About phone > tap **Build number** 7 times. Then Settings > Developer options > turn on **USB debugging**.
4. Plug the phone into the computer with USB and accept the prompt on the phone.
5. Press the green **Run** button in Android Studio and pick your phone. The app installs and opens. No Play Store needed.

If Android Studio cannot open the project, create a new "Empty Activity" project with package name `com.carlogbook.app`, then copy the `app/src/main` folder and the dependencies from `app/build.gradle.kts` into it.

## How to test the reminders quickly

1. Add a car with, for example, 60,000 km.
2. Add a service "Oil change" at 50,000 km, today's date, and **Every … km = 10000**.
3. Open the car, tap **Update km** and enter 59,500. That is within 1,000 km of the next oil change (at 60,000 km).
4. Tap the bell icon (Reminders). The oil change shows as "Due soon".
5. Tap the refresh icon at the top right to run the check now. You should get a notification. Allow notifications when Android asks.

## How the reminders work

- For each job (matched by name, ignoring upper and lower case), the app takes the most recent entry and adds your interval to it.
- It uses the most recent interval you entered for that job, so you do not need to retype it every time.
- A job with both km and months is due at whichever comes first.
- If notifications never arrive on some phones (Xiaomi, Huawei, Samsung power saving), allow the app to run in the background and turn off battery optimisation for it.

## Not in version 1 yet

- Choosing a car model and engine to fill in the service intervals automatically.
- Photos of receipts, cost tracking, charts.
- Export and import of your history to a file.

## Project layout

```
app/src/main/java/com/carlogbook/app/
  data/     Room database: Car, Service, DAOs, LastCar
  domain/   Reminder calculation and text formatting
  notify/   Notification channel, daily background check
  ui/       Compose screens: Cars, History, Service form, Car form, Upcoming
```
