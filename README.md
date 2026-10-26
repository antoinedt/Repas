# Repas

Application Android pour noter ce qu'on mange le soir.

- **Calendrier** – vue mensuelle ; touchez un jour et choisissez le plat dans une liste déroulante, ou ajoutez
  une nouvelle recette. Une nouvelle recette peut avoir un site web : la page est téléchargée et la recette
  (ingrédients, étapes, photo, durée) est enregistrée dans l'app, disponible hors ligne.
- **Recettes** – toutes les recettes, avec recherche, tri (A–Z, les plus mangées, récentes) et filtre par
  étiquette. Chaque recette peut recevoir des étiquettes (liste des étiquettes déjà utilisées, ou nouvelle).
- **Stats** – nombre de repas, part (%) de chaque recette et de chaque étiquette, évolution mensuelle,
  plat préféré par jour de la semaine. Périodes : 30 jours, 3 mois, 1 an, tout. Filtre par étiquette.
- **Idées** – propose des recettes pour ce soir ou demain d'après l'historique : plats préférés, ceux qu'il est
  temps de refaire, ce qui a été mangé récemment (évité), habitudes du jour, recettes jamais cuisinées.

- **Idée du jour par Claude** (onglet Idées) – avec une clé API Anthropic, Claude lit l'historique et les
  étiquettes, cherche sur le web une vraie recette et renvoie le plat, la raison et le lien. « Ce soir ! »
  l'enregistre (recette importée) et le note pour ce soir. En option : une idée chaque jour vers 10 h avec
  notification. Modèle `claude-opus-5-5`, recherche web ; quelques centimes par idée.

On peut aussi partager un lien de recette depuis le navigateur vers l'app.

## Sauvegarde

Dans **Recettes → ⋮** :

- **Exporter la sauvegarde…** – enregistre un fichier `repas-sauvegarde-AAAA-MM-JJ.json` où vous voulez
  (Téléchargements, Drive…).
- **Partager la sauvegarde…** – envoie le fichier par e-mail, messagerie, etc.
- **Sauvegarde automatique…** – choisissez un dossier (par ex. Documents ou un dossier synchronisé Drive) :
  le fichier `repas-sauvegarde.json` y est réécrit après chaque modification.
- **Importer une sauvegarde…** – fusionne un fichier dans les données actuelles : les recettes sont
  reconnues par leur nom (étiquettes et détails manquants complétés), les repas déjà notés ne sont pas
  dupliqués. Rien n'est effacé.

Le fichier est du JSON lisible :

```json
{
  "format": "repas",
  "version": 1,
  "exportedOn": "2026-10-21",
  "recipes": [
    { "name": "Curry de lentilles", "url": "https://…", "labels": ["Végétarien"],
      "ingredients": ["…"], "instructions": ["…"] }
  ],
  "dinners": [ { "date": "2026-10-20", "recipe": "Curry de lentilles" } ]
}
```

## Où sont les données ?

Dans la base privée de l'app sur le téléphone (inaccessible aux autres apps). Une mise à jour installée par-dessus
l'app les conserve ; une désinstallation les efface. D'où l'export / la sauvegarde automatique ci-dessus.

## Installation

Chaque push sur `main` construit un APK avec GitHub Actions et le publie dans les **Releases**.
Sur le téléphone, téléchargez `app-release.apk` depuis la dernière release et autorisez l'installation.

Les APK sont signés avec une clé fixe (secrets `RELEASE_KEYSTORE_BASE64` et `RELEASE_KEYSTORE_PASSWORD` du dépôt),
ce qui permet d'installer chaque nouvelle version par-dessus la précédente sans perdre les données.

## Compiler

Android SDK (API 35) et JDK 17 : `./gradlew assembleDebug`

Kotlin, Jetpack Compose (Material 3), Room, Jsoup (import via schema.org JSON-LD), Coil.
