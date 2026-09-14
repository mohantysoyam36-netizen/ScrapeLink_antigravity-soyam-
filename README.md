# Digital Formalization Network for E-Waste Collection
### Smart India Hackathon — Problem Statement 26229
**Compliant with Ministry of Environment, Forest & Climate Change (MoEFCC) E-Waste (Management) Rules, 2022 & CPCB EPR Framework**

---

## 1. Executive Summary & Problem Context
In India, approximately **80% of household and informal e-waste** is collected and segregated by informal scrap collectors (**Kabadiwalas**). However, authorized, formal recyclers registered with the **Central Pollution Control Board (CPCB)** often operate far below capacity due to the lack of a formalized, traceable, and direct supply pipeline.

This project delivers an **offline-first Android solution (Kotlin)** coupled with an **on-device ML model (TensorFlow Lite MobileNetV2)**, an **idempotent REST API backend (Node.js/Express + SQLite)**, and a **Recycler EPR Compliance Dashboard**. Rather than disintermediating informal collectors, this network digitizes their collection workflows into **Digital Material Passports**, providing CPCB EPR traceability from the streets of Seelampur and Dharavi all the way to authorized recycling plants.

---

## 2. End-to-End System Architecture

```
[ Kabadiwala On-Field Collection ]
   │
   ├─► 1. Camera Snap 📸 (Physical e-waste item or batch)
   │
   ├─► 2. On-Device TFLite Classifier 🧠 (MobileNetV2, 224x224 RGB)
   │      Auto-detects CPCB Schedule I Category (e.g. ITEW15, CEEW1, PCB)
   │      with confidence score; allows 1-tap manual override.
   │
   ├─► 3. Local Room Database Persistence 💾 (Offline-First Passport)
   │      Entity written with client-generated UUID, GPS, weight, timestamp.
   │      SyncStatus = LOCAL_ONLY.
   │
   └─► 4. Android WorkManager Job 🔄 (NetworkConstraint.CONNECTED)
          Enqueued with exponential backoff (10s, 20s, 40s...).
          │
      (Cellular / Wi-Fi Connectivity Available)
          ▼
[ Cloud / Edge REST API Backend ]
   │
   ├─► 5. POST /api/sync/batches (Idempotent Ingestion)
   │      Enforces state monotonicity:
   │      COLLECTED (1) -> IN_TRANSIT (2) -> HANDED_OVER (3) -> VERIFIED (4)
   │      Returns server acknowledgment { syncedBatchIds: [...] }.
   │
   ├─► 6. Local Room DB Update
   │      WorkManager marks local batch SyncStatus = SYNCED.
   │
   └─► 7. Recycler Custody & Physical Weighing 🏭
          Authorized recycler inspects batch, weighs scrap, and calls:
          POST /api/batches/:id/verify
          Generates cryptographic SHA-256 EPR Token (EPR-CPCB-...)
          and generates CPCB Schedule I fulfillment credit.
```

---

## 3. The Digital Material Passport Model
Every physical batch collected generates a digital material passport entity mapped cleanly to CPCB reporting categories:

| Passport Attribute | Type | Description |
| :--- | :--- | :--- |
| `batchId` | String (UUID) | Unique passport identifier generated on-device at physical pickup |
| `collectorId` | String | Formalized Kabadiwala profile identifier (e.g. `KAB-DL-2024-001`) |
| `itemCategory` | String | Human-readable category (e.g. `Smartphones & Mobile Devices`) |
| `cpcbCategoryCode` | String | Official CPCB Schedule I Code (`ITEW15`, `ITEW3`, `CEEW1`, `ITEW_PCB`, `BATT_LII`, `CBL_COP`) |
| `aiConfidence` | Float | Probability output from on-device MobileNetV2 (0.0 to 1.0) |
| `manualOverride` | Boolean | Audit flag indicating whether the kabadiwala manually adjusted the category |
| `estimatedWeightKg` | Double | Measured or estimated weight (kg) at field collection |
| `itemCount` | Int | Number of physical electronic units |
| `latitude` / `longitude` | Double | GPS coordinates captured at collection location |
| `locationAddress` | String | Geographical landmark (e.g. `Seelampur Ward 4, East Delhi`) |
| `timestamp` | Long | Monotonic millisecond epoch timestamp of physical collection |
| `handoverStatus` | Enum | Monotonic state: `COLLECTED` → `IN_TRANSIT` → `HANDED_OVER` → `VERIFIED_BY_RECYCLER` |
| `recyclerId` | String? | CPCB Recycler registration ID (assigned upon transfer) |
| `syncStatus` | Enum | Local lifecycle: `LOCAL_ONLY`, `PENDING_SYNC`, `SYNCED`, `SYNC_FAILED` |
| `qrPasscode` | String | 6-digit verification PIN and QR payload for recycler scanning |
| `recyclerVerificationHash` | String? | Cryptographic SHA-256 token generated upon recycler verification |

---

## 4. Key Architectural Decisions & Trade-Offs

### A. Backend Selection: Node.js/Express + SQLite vs. Django REST Framework
* **Decision**: We chose **Node.js (v22) with Express and built-in `node:sqlite`**.
* **Rationale & Trade-Offs**:
  1. **Zero-Impedance JSON Ingestion**: Android WorkManager sync payloads (arrays of batches with nested GPS coordinates, timestamps, and confidence scores) are handled natively as pure JSON objects without serializer friction.
  2. **Built-in `node:sqlite` in Node v22**: Zero external database dependencies or native compilation hurdles. Allows immediate, deterministic execution on standard environments.
  3. **High Concurrency for Bulk Sync**: Asynchronous non-blocking event loop handles sporadic, high-volume batch sync pushes when kabadiwalas regain mobile data.
  4. **Single-Service Dashboard & API**: Express serves both the JSON REST endpoints and the interactive Recycler Web Dashboard from a lightweight single-process binary.

### B. Offline Conflict-Resolution Strategy: Monotonic Lifecycle State Machine
* **Problem**: In spotty 2G/3G connectivity, a kabadiwala might mark a batch as `HANDED_OVER`, and later their device might reconnect and retry an earlier queued sync request with an older status (`COLLECTED`). Concurrently, the recycler may have already scanned and verified the batch (`VERIFIED_BY_RECYCLER`).
* **Strategy**:
  1. **Monotonic Status Ranking**: Every handover status has a strict rank:
     `COLLECTED (1) < IN_TRANSIT (2) < HANDED_OVER (3) < VERIFIED_BY_RECYCLER (4)`.
  2. **Non-Regressive State Transitions**: The backend enforces `incomingRank >= currentRank`. If a delayed mobile retry sends status `COLLECTED`, the server ignores the retrograde status while acknowledging the batch ID.
  3. **Recycler Verification Exclusivity**: Only authorized recyclers via authenticated endpoint `/api/batches/:id/verify` can transition a batch to `VERIFIED_BY_RECYCLER` and generate an EPR token.

### C. Low-Literacy Kabadiwala UX Design
* **Problem**: Many informal scrap collectors face literacy barriers and cannot read complex forms or technical menus.
* **Strategy**:
  1. **Color-Coded Visual Card Actions**:
     - 📸 **Big Green Card**: *"Naya Maal Scan Karein"* (Capture item)
     - 🚚 **Big Blue Card**: *"Recycler Ko Bechein"* (Handover batch)
     - ☁️ **Real-Time Sync Badge**: Green checkmark (All Synced) vs. Yellow hourglass (Offline Saved).
  2. **Visual Icon Grid for Category Overrides**:
     - 📱 Mobile Phone (`ITEW15`)
     - 💻 Laptop (`ITEW3`)
     - 🧩 Circuit Board (`ITEW_PCB`)
     - 🖥️ TV/Monitor (`CEEW1`)
     - 🔋 Battery (`BATT_LII`)
     - 🔌 Cables (`CBL_COP`)
  3. **Preset Quick-Tap Weights**: One-tap buttons for `1 Kg`, `5 Kg`, `10 Kg`, and `25 Kg`.
  4. **Bilingual Hindi & English Audio/Visual cues**.

---

## 5. CPCB E-Waste (Management) Rules, 2022 Compliance Mapping

All categories within the app and database map directly to **Schedule I** of India's **E-Waste (Management) Rules, 2022** and the **Battery Waste Management Rules (BWMR), 2022**:

| App Category Key | CPCB Schedule I Code | Official Category Description | Regulatory Framework |
| :--- | :--- | :--- | :--- |
| `mobile_phone` | **ITEW15** | Cellular Telephones / Smartphones | E-Waste Rules 2022 |
| `laptop_computer` | **ITEW3** | Laptops & Notebooks | E-Waste Rules 2022 |
| `pcb_circuit_board` | **ITEW_PCB** | Printed Circuit Boards & Electronic Fractions | E-Waste Rules 2022 |
| `crt_lcd_monitor` | **CEEW1** | Television sets (CRT, LCD, LED) & Monitors | E-Waste Rules 2022 |
| `battery_pack` | **BATT_LII** | Lithium-ion & Lead-Acid Portable Batteries | BWMR 2022 |
| `copper_cable_wire` | **CBL_COP** | Copper wiring harness fractions | E-Waste Rules 2022 |
| `printer_peripheral` | **ITEW4** | Printers, Cartridges & Scanners | E-Waste Rules 2022 |
| `household_appliance_small` | **CEEW_SHA** | Small Consumer Electrical & Electronics | E-Waste Rules 2022 |

---

## 6. How to Run the System

### 1. Launch the Backend API & Recycler Dashboard
```bash
cd backend
npm install
node src/server.js
```
- Server URL: `http://localhost:3000`
- Interactive Recycler Web Dashboard: `http://localhost:3000/index.html`

### 2. Verify Backend Endpoints
```bash
cd backend
node test_api.js
```
Executes all 6 integration tests: Dashboard serving, Batches query, WorkManager ingestion, Passport lookup, Recycler verification, and EPR metric calculation.

### 3. Open & Build the Android App (Kotlin)
1. Open **Android Studio** (Hedgehog or newer).
2. Select **Open an existing project** and navigate to `ewaste_formalization_network/android`.
3. Gradle will sync dependencies:
   - `androidx.room:room-runtime:2.6.1`
   - `androidx.work:work-runtime-ktx:2.9.0`
   - `org.tensorflow:tensorflow-lite:2.14.0`
   - `com.squareup.retrofit2:retrofit:2.9.0`
   - `com.google.zxing:core:3.5.3`
4. Run the app on an Android Emulator or physical device:
   - **Emulator network loopback**: `http://10.0.2.2:3000/` (preconfigured default).
   - **Physical device over Wi-Fi**: Update the Base URL in the app's Sync Status screen to your local machine IP (e.g. `http://192.168.1.15:3000/`).

### 4. Retrain or Export MobileNetV2 ML Pipeline (Python 3.11)
```bash
cd ml_pipeline
python create_tflite_model.py
# For full dataset transfer learning:
python train_mobilenet_v2.py
```
Outputs `model.tflite` directly into `android/app/src/main/assets/model.tflite`.
