<h1 align="center">🐾 Asociación de Asís</h1>

<p align="center">
  <b>Animal rescue and adoption management system for <a href="https://animalesdeasis.com/" target="_blank">Asociación de Asís</a></b>
</p>

---

## 📌 Project Overview

This desktop application was built for **Asociación de Asís**, an animal rescue and adoption organization based in Costa Rica.

It allows the team to **register and manage rescued animals, track vaccinations, sync records with Firebase, and generate professional reports**, all through an intuitive interface designed to work both **online and offline**.

Besides being a tailor-made tool for Asociación de Asís, this repository can also serve as a **starter template for other desktop management applications**.

---

## 🎯 Purpose of the System

The goal of this project is to provide a **comprehensive offline-first solution** that enables Asociación de Asís to:

- Register and manage data for rescued animals.
- Track vaccination records and medical follow-ups.
- Automatically sync data with Firebase whenever internet is available.
- View yearly admission and adoption statistics.
- Export clean, readable **CSV and PDF reports** for presentations and stakeholders.

---

## 🧩 Key Features

### 🐶 Animal Management
- Register animals with details like species, name, age, chip/barcode, rescue reason, etc.
- Track neutering and adoption status.
- Link animals to their rescue location (province/place).
- Sync animal records with Firebase.

### 💉 Vaccine Tracking
- Record vaccinations per animal.
- Support for vaccine name and administration date.
- Each vaccine is synced independently if needed.

### 🌍 Geographic Linking
- Provinces and places automatically loaded from a national API.
- Animals are linked to the specific location they were rescued from.

### 🔄 Bidirectional Sync (Offline-First)
- Syncs with Firebase on:
  - Application startup (if internet is available).
  - Every 24 hours (automated scheduler).
- Sync process:
  - **Push**: Uploads unsynced local data to Firebase.
  - **Pull**: Downloads new Firebase data if not found locally.
- Local-first logic to avoid data overwrites.

### 📊 Statistics & Reporting
- Monthly admissions by year.
- Total animals rescued per year.
- Yearly adoption rate in percentage.
- Export reports to **CSV** and **PDF**:
  - User selects where to save using a file chooser.
  - Output is clean, sectioned, and human-friendly.

---

## 🏗️ Technologies Used

| Area              | Technology        |
|-------------------|-------------------|
| Local Database    | SQLite            |
| Remote Sync       | Google Cloud Firestore SDK |
| GUI               | JavaFX            |
| Backend           | Java 21 (LTS)     |

### 🔐 Firebase Sync Setup

To enable **Firebase cloud synchronization**:

1. In the Firebase Console, generate a service-account private key (JSON).
2. Choose a long random passphrase and export it. There is no default, and the
   tool refuses to run without one:
   ```bash
   export ANIMALESDEASIS_CRED_KEY='...'      # bash
   $env:ANIMALESDEASIS_CRED_KEY = '...'      # PowerShell
   ```
3. Encrypt the JSON into `src/main/resources/FireConfig/firebase-credentials.enc`:
   ```bash
   java -cp target/classes \
     com.asosiaciondeasis.animalesdeasis.Config.FirebaseCredentialsEncryptor path/to/service-account.json
   ```
4. Delete the plaintext JSON, and set the **same** passphrase as
   `ANIMALESDEASIS_CRED_KEY` on every machine that synchronises.

Without step 4 the application runs local-only. That is deliberate: it used to
fall back to a key compiled into the source, so every published build decrypted
with a passphrase anyone could read in this repository.

> **Upgrading?** Bundles from before this change no longer open, and the
> application says so on startup. The service-account key they hold has to be
> **revoked**, not merely re-encrypted — see [SECURITY.md](SECURITY.md).

> The encrypted bundle and any service-account JSON are **never committed**; the
> git history was checked and neither ever has been.

---

## 🛠️ Building, Testing & Packaging

Requires **JDK 21** (a *Full* JDK that includes JavaFX, e.g. Liberica Full, is used in CI).

From an IDE, run **`Main`** — pressing Run works with no launch configuration.

```bash
# Run the unit tests (JUnit 5 + Mockito)
./mvnw test

# Run the application
./mvnw javafx:run

# Build a native installer for the current OS (Windows .exe / macOS .dmg / Linux .deb)
./mvnw clean package
./mvnw jpackage:jpackage      # output in target/installer/
```

The application version is controlled by the `app.version` property in `pom.xml`.

### 🚀 Continuous Integration & Releases
GitHub Actions ([`.github/workflows/workflow-CI.yml`](.github/workflows/workflow-CI.yml)):
- **Every push / PR to `main`** → compile and run the test suite.
- **Pushing a `v*` tag** (e.g. `git tag v1.0.0 && git push origin v1.0.0`) → builds
  native installers on Windows, macOS and Linux and publishes them to a GitHub Release.

To let CI produce Firebase-enabled installers, add a repository secret
`FIREBASE_CREDENTIALS_ENC` containing the base64 of the encrypted bundle (optional;
without it, installers build in offline-only mode). See [SECURITY.md](SECURITY.md).

---

## 📦 Project Structure

```
src
├── Abstraccions/         # Interfaces for DAOs and Services (Animals, Places, Statistics, Vaccines)
├── Config/               # Configuration (DB, Firebase, Credentials, Factories)
├── Controller/           # JavaFX Controllers (Portal, Sidebar, Animal, Statistic, Vaccine)
├── DAO/                  # Data Access Objects (Importers, Animals, Places, Statistics, Vaccine)
├── Model/                # Data Models (Animal, Place, Vaccine)
├── Service/              # Business Logic (SyncService, Animal, Place, Statistics, Vaccine)
└── Util/                 # Utilities (Barcode, Date, Network, Exporters, Helpers)
```
---

## 🤝 Contributing
This repository is not only a working system for Asociación de Asís but can also be adapted as a **template for desktop management applications**.

---
