# uWiiiNav 🚗🧭

**uWiiiNav** ist eine moderne, Open-Source-basierte Navigations-App für Android und Android Auto, die auf OpenStreetMap (OSM) und MapLibre setzt.

Entwickelt für eine schlanke, ablenkungsfreie und datenschutzfreundliche Navigation im Auto – mit 100 % Fokus auf Performance (z. B. auf Flaggschiffen wie dem Galaxy S25 Ultra).

---

## 🌟 Kernfunktionen

- **OpenStreetMap & MapLibre:** Vektorkartenanzeige mit stufenlosem Zoom, flüssiger 3D-Kippung und Kompass-Fahrtrichtungsausrichtung.
- **Cockpit-Tacho:** Live-Geschwindigkeitsanzeige (km/h) mit dynamischer Farbanpassung basierend auf GPS-Genauigkeit.
- **Kein Tracking & 100 % Datenschutz:** Alle Routen- und Standortdaten bleiben privat auf deinem Smartphone.
- **GitHub Auto-Updates:** Die App prüft bei Bedarf automatisch auf neue Releases auf GitHub und ermöglicht ein 1-Klick-Update ohne Google Play Store.
- **Vorbereitet für Offline-Karten & Android Auto:** Modulare Architektur für GraphHopper-Offline-Routing und Android Auto Car-App-Integration.

---

## 🛠️ Technologie-Stack

- **Sprache:** Kotlin (JVM 17)
- **UI:** Jetpack Compose (Material 3) mit Dark-Cockpit-Design
- **Karten-SDK:** [MapLibre Native Android](https://maplibre.org/)
- **Standort:** Google Play Services Location (`FusedLocationProviderClient`)
- **Netzwerk / Updates:** OkHttp 4 & GitHub REST API
- **Build-System:** Gradle 8.10+ mit Version Catalog (`libs.versions.toml`)

---

## 🚀 Installation & Erste Schritte

### 1. In Android Studio öffnen
1. Klone das Repository oder öffne den Ordner `uWiiiNav` direkt in **Android Studio**.
2. Lass Gradle die Abhängigkeiten synchronisieren.
3. Verbinde dein Smartphone (z. B. Samsung Galaxy S25 Ultra) per USB (mit aktiviertem USB-Debugging) oder wähle einen Emulator.
4. Klicke auf **„Run“ (Grüner Pfeil)**.

### 2. Automatische Builds über GitHub Releases
Sobald ein Git-Tag wie `v1.0.0` gepusht wird, baut GitHub Actions automatisch eine fertige Installations-APK (`uWiiiNav.apk`) und stellt sie im Reiter **Releases** bereit.

---

## 📄 Lizenz

Dieses Projekt ist lizenziert unter der MIT-Lizenz.
