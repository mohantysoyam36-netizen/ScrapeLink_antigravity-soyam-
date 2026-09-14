const { DatabaseSync } = require('node:sqlite');
const path = require('path');
const fs = require('fs');

const dbDir = path.join(__dirname, '..', 'data');
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true });
}

const dbPath = path.join(dbDir, 'ewaste_formalization.db');
const db = new DatabaseSync(dbPath);

// Initialize Tables
db.exec(`
  CREATE TABLE IF NOT EXISTS collectors (
    collector_id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    operating_territory TEXT,
    is_kyc_verified INTEGER DEFAULT 1,
    total_collected_kg REAL DEFAULT 0,
    created_at INTEGER
  );

  CREATE TABLE IF NOT EXISTS recyclers (
    recycler_id TEXT PRIMARY KEY,
    company_name TEXT NOT NULL,
    cpcb_registration_number TEXT UNIQUE NOT NULL,
    facility_address TEXT NOT NULL,
    authorized_categories TEXT NOT NULL,
    contact_phone TEXT,
    is_epr_certified INTEGER DEFAULT 1,
    created_at INTEGER
  );

  CREATE TABLE IF NOT EXISTS batches (
    batch_id TEXT PRIMARY KEY,
    collector_id TEXT NOT NULL,
    item_category TEXT NOT NULL,
    cpcb_category_code TEXT NOT NULL,
    ai_confidence REAL,
    manual_override INTEGER DEFAULT 0,
    estimated_weight_kg REAL NOT NULL,
    item_count INTEGER DEFAULT 1,
    latitude REAL,
    longitude REAL,
    location_address TEXT,
    timestamp INTEGER NOT NULL,
    handover_status TEXT NOT NULL,
    recycler_id TEXT,
    qr_passcode TEXT,
    recycler_verification_hash TEXT,
    verified_weight_kg REAL,
    created_at INTEGER,
    updated_at INTEGER,
    FOREIGN KEY(collector_id) REFERENCES collectors(collector_id),
    FOREIGN KEY(recycler_id) REFERENCES recyclers(recycler_id)
  );

  CREATE TABLE IF NOT EXISTS epr_verifications (
    verification_id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    recycler_id TEXT NOT NULL,
    inspector_name TEXT,
    cpcb_category_code TEXT NOT NULL,
    verified_weight_kg REAL NOT NULL,
    verification_hash TEXT NOT NULL,
    verification_timestamp INTEGER NOT NULL,
    notes TEXT,
    FOREIGN KEY(batch_id) REFERENCES batches(batch_id)
  );
`);

// Seed Default Collectors and Recyclers if empty
const collectorCountStmt = db.prepare('SELECT COUNT(*) as count FROM collectors');
const { count: collectorCount } = collectorCountStmt.get();

if (collectorCount === 0) {
  const insertCollector = db.prepare(`
    INSERT INTO collectors (collector_id, full_name, phone_number, operating_territory, is_kyc_verified, total_collected_kg, created_at)
    VALUES (?, ?, ?, ?, 1, 0, ?)
  `);
  insertCollector.run(
    'KAB-DL-2024-001',
    'Ramesh Kumar (रमेश कुमार)',
    '+91 98765 43210',
    'Seelampur Ward 4, East Delhi',
    Date.now()
  );
  insertCollector.run(
    'KAB-MH-2024-042',
    'Sunil Jadhav (सुनील जाधव)',
    '+91 91234 56789',
    'Dharavi Scrap Transit Sector 5, Mumbai',
    Date.now()
  );
}

const recyclerCountStmt = db.prepare('SELECT COUNT(*) as count FROM recyclers');
const { count: recyclerCount } = recyclerCountStmt.get();

if (recyclerCount === 0) {
  const insertRecycler = db.prepare(`
    INSERT INTO recyclers (recycler_id, company_name, cpcb_registration_number, facility_address, authorized_categories, contact_phone, is_epr_certified, created_at)
    VALUES (?, ?, ?, ?, ?, ?, 1, ?)
  `);
  insertRecycler.run(
    'REC-CPCB-001',
    'Eco-Greens Formal Recycling Hub',
    'CPCB/EW-REC/2023/DL-0081',
    'Plot 12, Okhla Industrial Area Phase III, New Delhi - 110020',
    'ITEW15,ITEW3,CEEW1,PCB,BATT_LII',
    '+91 11 2681 4400',
    Date.now()
  );
  insertRecycler.run(
    'REC-CPCB-002',
    'Bharat EPR Resource Recovery Ltd',
    'CPCB/EW-REC/2022/UP-0194',
    'Site IV, Sahibabad Industrial Area, Ghaziabad, UP - 201010',
    'ITEW15,CEEW1,PCB,CBL_COP',
    '+91 120 415 8890',
    Date.now()
  );
}

module.exports = db;
