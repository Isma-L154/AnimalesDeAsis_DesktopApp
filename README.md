# 🐾 Asociación de Asís - Animal Management System

## 📌 Project Overview

This desktop application was built for **Asociación de Asís**, an animal rescue and adoption organization based in Costa Rica. It allows the team to register and manage rescued animals, track vaccinations, sync records with Firebase, and generate statistical reports, all through an intuitive interface designed to work both **online and offline**.

---

## 🎯 Purpose of the System

The goal of this project is to provide a **comprehensive offline-first solution** that enables Asociación de Asís to:

- Register and manage data for rescued animals.
- Track vaccination records and medical follow-ups.
- Automatically sync data with Firebase whenever internet is available.
- View yearly admission and adoption statistics.
- Export clean, readable CSV reports for presentations and stakeholders.
- Work seamlessly offline using a local SQLite database.

---

## 🧩 Key Features

### 🐶 Animal Management
- Register animals with details like species, name, age, chip/barcode, rescue reason, etc.
- Track neutering and adoption status.
- Link animals to their rescue location (province/place).
- Soft-delete system using `active` flag.
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
- CSV export with all key statistics:
  - User selects where to save using a file chooser.
  - Output is clean, sectioned, and human-friendly.

---

## 🏗️ Technologies Used

| Area              | Technology        |
|-------------------|-------------------|
| Local Database    | SQLite            |
| Remote Sync       | Google Cloud Firestore SDK |
| GUI               | JavaFX        |
| Backend           | Java 24          |


---

## 📦 Project Structure

````markdown
src/
├── dao/        # Data access layer (AnimalDAO, VaccineDAO, etc.)
├── model/      # POJOs (Animal, Vaccine, Place, etc.)
├── services/BL   # Core logic (SyncService, CsvExporter)
├── firebase/   # Firebase initialization
├── database/   # SQLite setup
├── ui/         # Swing UI panels and windows
└── utils/      # Helpers (network, formatting, validation)

