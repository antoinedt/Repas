# Dinners

Android app to keep track of what we eat for dinner.

- **Calendar** – month view; tap a day and pick what you ate from a searchable dropdown, or add a new recipe.
  A new recipe can have a website: the page is downloaded and the recipe (ingredients, steps, photo, time)
  is saved inside the app, so it stays available offline.
- **Recipes** – every saved recipe, searchable and sortable (A–Z, most eaten, recent). Open one to read it,
  edit it, open the original site or re-import it.
- **Stats** – dinners logged, share (%) of each recipe, monthly trend of the top recipes, favourite by weekday.
  Periods: 30 days, 3 months, year, all time.
- **Suggest** – ranks recipes for tonight or tomorrow from your history: favourites, recipes that are "due"
  again given how often you usually have them, what you ate recently (avoided), weekday habits, and saved
  recipes you've never cooked. Shuffle for alternatives, one tap to plan it.

You can also share a recipe link from your browser to the app to save it.

## Install

Every push to `main` builds an APK with GitHub Actions and publishes it as a release.
On your phone open the repo's **Releases**, download `app-release.apk`, and allow installing from your browser.

## Build locally

Requires the Android SDK (API 35) and JDK 17:

```
./gradlew assembleDebug
```

Tech: Kotlin, Jetpack Compose (Material 3), Room, Jsoup (recipe import via schema.org JSON-LD), Coil.
