# Suivi CND (Android)

Reads "Suivi de l'élève" from Collège Notre-Dame's PedNET portal and shows
it as a clean, collapsible list. Credentials stay encrypted on the phone; the
app only talks to the college portal.

## Get the APK (no Android Studio needed)

1. Create a **private** GitHub repo and upload this whole folder
   (keep the `.github` folder and `gradlew`).
2. The **Actions** tab runs "Build APK" automatically (≈5 min).
   Open the finished run → **Artifacts** → `SuiviCND-apk` → download.
3. Unzip it, copy `app-debug.apk` to the phone (or download it there),
   open it and allow "install unknown apps" when Android asks.

Every later push builds a new APK that installs over the old one
(settings and saved entries are kept).

Alternative: open the folder in Android Studio → Run.

## Use

1. First launch opens **Paramètres**: enter the portal username and password → Enregistrer.
2. Tap **Synchroniser**. The first sync imports everything (≈1 min, it opens each
   entry to read its detail). Later syncs are quick and flag new entries "Nouveau".
3. Tap an entry to expand it. Filter chips: Non conformes / Assiduité / Conformes.

## If sync fails
Paramètres → turn on **Afficher le navigateur pendant la synchro**, sync again,
and watch the page. "Voir le journal" in the error card shows each step.
