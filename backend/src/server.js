const express = require('express');
const cors = require('cors');
const path = require('path');
const crypto = require('crypto');
const db = require('./db');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '..', 'public')));

// Lifecycle Rank Mapping for State Monotonicity
const STATUS_RANK = {
  COLLECTED: 1,
  IN_TRANSIT: 2,
  HANDED_OVER: 3,
  VERIFIED_BY_RECYCLER: 4
};

// CPCB Schedule I Category Metadata (E-Waste Rules 2022)
const CPCB_CATEGORIES = {
  ITEW15: 'Cellular Telephones / Smartphones',
  ITEW3: 'Laptops & Notebooks',
  ITEW_PCB: 'Printed Circuit Boards (Precious Fractions)',
  CEEW1: 'Televisions & CRT / LCD / LED Displays',
  BATT_LII: 'Lithium-Ion / Storage Batteries (BWMR 2022)',
  CBL_COP: 'Copper Cables & Harness Wiring',
  ITEW4: 'Printers, Cartridges & Scanners',
  CEEW_SHA: 'Small Household Electrical & Electronics'
};

// Helper: Award Reputation Rating and Incentive Points to Collector
function awardCollectorIncentive(collectorId, batchId, eventType, weightKg = 1.0, ratingDelta = 0.1, basePoints = 50) {
  try {
    if (!collectorId) return null;

    // Check if incentive for this batch & eventType was already awarded
    const existingIncentive = db.prepare(
      'SELECT incentive_id FROM collector_incentives WHERE collector_id = ? AND batch_id = ? AND event_type = ?'
    ).get(collectorId, batchId, eventType);

    if (existingIncentive) {
      return null; // Already awarded
    }

    const collector = db.prepare('SELECT * FROM collectors WHERE collector_id = ?').get(collectorId);
    if (!collector) return null;

    // Calculate points: basePoints + 10 points per kg
    const weightBonus = Math.round(weightKg * 10);
    const pointsAwarded = basePoints + weightBonus;
    const currentRating = collector.rating || 4.5;
    const newRating = Math.min(5.0, parseFloat((currentRating + ratingDelta).toFixed(2)));
    const newTotalPoints = (collector.incentive_points || 0) + pointsAwarded;
    const newHandovers = (collector.successful_handovers || 0) + (eventType === 'HANDOVER_COMPLETED' ? 1 : 0);

    // Determine tier
    let newTier = 'BRONZE';
    if (newTotalPoints >= 600) {
      newTier = 'GOLD';
    } else if (newTotalPoints >= 250) {
      newTier = 'SILVER';
    }

    // Update collector stats
    db.prepare(`
      UPDATE collectors SET
        rating = ?,
        successful_handovers = ?,
        incentive_points = ?,
        incentive_tier = ?
      WHERE collector_id = ?
    `).run(newRating, newHandovers, newTotalPoints, newTier, collectorId);

    // Record audit event in collector_incentives
    const incentiveId = 'INC-' + crypto.randomBytes(4).toString('hex').toUpperCase();
    const desc = eventType === 'HANDOVER_COMPLETED'
      ? `Material successfully handed over to CPCB authorized recycler (${weightKg} kg)`
      : `Recycler dock verification completed & EPR credit approved (${weightKg} kg)`;

    db.prepare(`
      INSERT INTO collector_incentives (
        incentive_id, collector_id, batch_id, event_type, points_awarded, rating_increment, description, timestamp
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(incentiveId, collectorId, batchId, eventType, pointsAwarded, ratingDelta, desc, Date.now());

    return {
      incentiveId,
      newRating,
      newTotalPoints,
      newTier,
      pointsAwarded
    };
  } catch (err) {
    console.error('Error awarding collector incentive:', err);
    return null;
  }
}

// 1. Ingest Synced Batches from Android WorkManager
app.post('/api/sync/batches', (req, res) => {
  try {
    const { collectorId, batches = [], clientSyncTimestamp } = req.body;
    if (!batches || !Array.isArray(batches)) {
      return res.status(400).json({ success: false, message: 'Invalid batches payload' });
    }

    const syncedBatchIds = [];
    const now = Date.now();

    const findBatchStmt = db.prepare('SELECT handover_status, collector_id FROM batches WHERE batch_id = ?');
    const insertBatchStmt = db.prepare(`
      INSERT INTO batches (
        batch_id, collector_id, item_category, cpcb_category_code, ai_confidence,
        manual_override, estimated_weight_kg, item_count, latitude, longitude,
        location_address, timestamp, handover_status, recycler_id, qr_passcode,
        recycler_verification_hash, verified_weight_kg, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const updateBatchStmt = db.prepare(`
      UPDATE batches SET
        item_category = ?, cpcb_category_code = ?, ai_confidence = ?, manual_override = ?,
        estimated_weight_kg = ?, item_count = ?, latitude = ?, longitude = ?,
        location_address = ?, handover_status = ?, recycler_id = COALESCE(?, recycler_id),
        qr_passcode = ?, updated_at = ?
      WHERE batch_id = ?
    `);

    const updateCollectorWeightStmt = db.prepare(`
      UPDATE collectors SET total_collected_kg = total_collected_kg + ? WHERE collector_id = ?
    `);

    batches.forEach(b => {
      const existing = findBatchStmt.get(b.batchId);
      const targetCollector = b.collectorId || collectorId || 'KAB-DL-2024-001';

      if (!existing) {
        insertBatchStmt.run(
          b.batchId,
          targetCollector,
          b.itemCategory,
          b.cpcbCategoryCode,
          b.aiConfidence || 0.9,
          b.manualOverride ? 1 : 0,
          b.estimatedWeightKg || 1.0,
          b.itemCount || 1,
          b.latitude || 28.6692,
          b.longitude || 77.2764,
          b.locationAddress || 'Delhi NCR Depot',
          b.timestamp || now,
          b.handoverStatus || 'COLLECTED',
          b.recyclerId || null,
          b.qrPasscode || '123456',
          b.recyclerVerificationHash || null,
          null,
          now,
          now
        );
        try {
          updateCollectorWeightStmt.run(b.estimatedWeightKg || 1.0, targetCollector);
        } catch (e) {}

        if (b.handoverStatus === 'HANDED_OVER' || b.handoverStatus === 'VERIFIED_BY_RECYCLER') {
          awardCollectorIncentive(targetCollector, b.batchId, 'HANDOVER_COMPLETED', b.estimatedWeightKg || 1.0, 0.1, 50);
        }
      } else {
        const currentRank = STATUS_RANK[existing.handover_status] || 1;
        const incomingRank = STATUS_RANK[b.handoverStatus] || 1;
        const resolvedStatus = incomingRank >= currentRank ? b.handoverStatus : existing.handover_status;

        updateBatchStmt.run(
          b.itemCategory,
          b.cpcbCategoryCode,
          b.aiConfidence || 0.9,
          b.manualOverride ? 1 : 0,
          b.estimatedWeightKg || 1.0,
          b.itemCount || 1,
          b.latitude || 28.6692,
          b.longitude || 77.2764,
          b.locationAddress || 'Delhi NCR Depot',
          resolvedStatus,
          b.recyclerId || null,
          b.qrPasscode || '123456',
          now,
          b.batchId
        );

        if (existing.handover_status !== 'HANDED_OVER' && existing.handover_status !== 'VERIFIED_BY_RECYCLER' &&
            (resolvedStatus === 'HANDED_OVER' || resolvedStatus === 'VERIFIED_BY_RECYCLER')) {
          awardCollectorIncentive(existing.collector_id || targetCollector, b.batchId, 'HANDOVER_COMPLETED', b.estimatedWeightKg || 1.0, 0.1, 50);
        }
      }
      syncedBatchIds.push(b.batchId);
    });

    res.json({
      success: true,
      message: `Successfully processed ${syncedBatchIds.length} batch records.`,
      syncedBatchIds,
      serverTimestamp: now
    });
  } catch (err) {
    console.error('Error during batch sync:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

// 2. Query Batches
app.get('/api/batches', (req, res) => {
  try {
    const { status, collectorId, recyclerId } = req.query;
    let query = `
      SELECT b.*, c.full_name as collector_name, c.rating as collector_rating,
             c.incentive_tier as collector_tier, c.successful_handovers as collector_handovers
      FROM batches b
      LEFT JOIN collectors c ON b.collector_id = c.collector_id
      WHERE 1=1
    `;
    const params = [];

    if (status) {
      query += ' AND b.handover_status = ?';
      params.push(status);
    }
    if (collectorId) {
      query += ' AND b.collector_id = ?';
      params.push(collectorId);
    }
    if (recyclerId) {
      query += ' AND b.recycler_id = ?';
      params.push(recyclerId);
    }

    query += ' ORDER BY b.created_at DESC';
    const rows = db.prepare(query).all(...params);
    res.json({ success: true, count: rows.length, batches: rows });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// 3. Single Batch Passport Detail
app.get('/api/batches/:id', (req, res) => {
  try {
    const batch = db.prepare(`
      SELECT b.*, c.full_name as collector_name, c.phone_number as collector_phone,
             c.rating as collector_rating, c.incentive_tier as collector_tier,
             c.successful_handovers as collector_handovers, c.incentive_points as collector_points,
             r.company_name as recycler_company, r.cpcb_registration_number
      FROM batches b
      LEFT JOIN collectors c ON b.collector_id = c.collector_id
      LEFT JOIN recyclers r ON b.recycler_id = r.recycler_id
      WHERE b.batch_id = ?
    `).get(req.params.id);

    if (!batch) {
      return res.status(404).json({ success: false, message: 'Batch passport not found' });
    }

    const verification = db.prepare('SELECT * FROM epr_verifications WHERE batch_id = ?').get(req.params.id);
    res.json({ success: true, passport: batch, verification });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// 4. Register Recycler
app.post('/api/recyclers/register', (req, res) => {
  try {
    const { recyclerId, companyName, cpcbRegistrationNumber, facilityAddress, authorizedCategories, contactPhone } = req.body;
    if (!recyclerId || !companyName || !cpcbRegistrationNumber) {
      return res.status(400).json({ success: false, message: 'Missing required fields' });
    }

    db.prepare(`
      INSERT INTO recyclers (recycler_id, company_name, cpcb_registration_number, facility_address, authorized_categories, contact_phone, is_epr_certified, created_at)
      VALUES (?, ?, ?, ?, ?, ?, 1, ?)
    `).run(recyclerId, companyName, cpcbRegistrationNumber, facilityAddress || '', authorizedCategories || 'ITEW15,CEEW1,PCB', contactPhone || '', Date.now());

    res.json({ success: true, message: 'Recycler registered successfully' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// 5. List Recyclers
app.get('/api/recyclers', (req, res) => {
  try {
    const rows = db.prepare('SELECT * FROM recyclers ORDER BY company_name ASC').all();
    res.json({ success: true, recyclers: rows });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// 6. Recycler Verification & EPR Certificate Generation
app.post('/api/batches/:id/verify', (req, res) => {
  try {
    const batchId = req.params.id;
    const { recyclerId, verifiedWeightKg, inspectorName = 'Authorized CPCB Officer', notes = 'Physical inspection passed' } = req.body;

    const batch = db.prepare('SELECT * FROM batches WHERE batch_id = ?').get(batchId);
    if (!batch) {
      return res.status(404).json({ success: false, message: 'Batch not found' });
    }

    const verifiedWeight = verifiedWeightKg ? parseFloat(verifiedWeightKg) : batch.estimated_weight_kg;
    const timestamp = Date.now();

    const hashData = `${batchId}|${batch.collector_id}|${recyclerId || batch.recycler_id}|${batch.cpcb_category_code}|${verifiedWeight}|${timestamp}`;
    const verificationHash = 'EPR-CPCB-' + crypto.createHash('sha256').update(hashData).digest('hex').substring(0, 20).toUpperCase();
    const verificationId = 'VRF-' + crypto.randomBytes(4).toString('hex').toUpperCase();

    db.prepare(`
      INSERT INTO epr_verifications (verification_id, batch_id, recycler_id, inspector_name, cpcb_category_code, verified_weight_kg, verification_hash, verification_timestamp, notes)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      verificationId,
      batchId,
      recyclerId || batch.recycler_id || 'REC-CPCB-001',
      inspectorName,
      batch.cpcb_category_code,
      verifiedWeight,
      verificationHash,
      timestamp,
      notes
    );

    db.prepare(`
      UPDATE batches SET
        handover_status = 'VERIFIED_BY_RECYCLER',
        recycler_id = COALESCE(?, recycler_id),
        verified_weight_kg = ?,
        recycler_verification_hash = ?,
        updated_at = ?
      WHERE batch_id = ?
    `).run(
      recyclerId || batch.recycler_id || 'REC-CPCB-001',
      verifiedWeight,
      verificationHash,
      timestamp,
      batchId
    );

    // Award Collector verification bonus incentive
    const incentiveResult = awardCollectorIncentive(batch.collector_id, batchId, 'RECYCLER_VERIFIED', verifiedWeight, 0.05, 30);

    res.json({
      success: true,
      message: 'Batch successfully verified and EPR credit created!',
      batchId,
      verificationId,
      verificationHash,
      verifiedWeightKg: verifiedWeight,
      cpcbCategory: batch.cpcb_category_code,
      incentive: incentiveResult,
      timestamp
    });
  } catch (err) {
    console.error('Verification error:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

// 7. EPR Compliance Aggregated Metrics (CPCB E-Waste Rules 2022)
app.get('/api/epr/metrics', (req, res) => {
  try {
    const totalCollected = db.prepare('SELECT COALESCE(SUM(estimated_weight_kg), 0) as total FROM batches').get().total;
    const totalVerified = db.prepare("SELECT COALESCE(SUM(verified_weight_kg), 0) as total FROM batches WHERE handover_status = 'VERIFIED_BY_RECYCLER'").get().total;
    const totalBatches = db.prepare('SELECT COUNT(*) as count FROM batches').get().count;
    const verifiedBatches = db.prepare("SELECT COUNT(*) as count FROM batches WHERE handover_status = 'VERIFIED_BY_RECYCLER'").get().count;
    const totalCollectors = db.prepare('SELECT COUNT(DISTINCT collector_id) as count FROM batches').get().count;

    const categoryBreakdown = db.prepare(`
      SELECT 
        cpcb_category_code,
        COUNT(*) as batch_count,
        COALESCE(SUM(estimated_weight_kg), 0) as estimated_kg,
        COALESCE(SUM(verified_weight_kg), 0) as verified_kg
      FROM batches
      GROUP BY cpcb_category_code
    `).all();

    const enrichedBreakdown = categoryBreakdown.map(item => ({
      ...item,
      category_name: CPCB_CATEGORIES[item.cpcb_category_code] || item.cpcb_category_code
    }));

    res.json({
      success: true,
      summary: {
        totalCollectedKg: parseFloat(totalCollected.toFixed(2)),
        totalVerifiedKg: parseFloat(totalVerified.toFixed(2)),
        totalBatches,
        verifiedBatches,
        pendingVerificationBatches: totalBatches - verifiedBatches,
        formalizedKabadiwalas: totalCollectors || 1,
        eprCertificatesIssued: verifiedBatches
      },
      categoryBreakdown: enrichedBreakdown
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// 8. Collector Reputation, Ratings & Incentive Metrics
app.get('/api/collectors', (req, res) => {
  try {
    const collectors = db.prepare(`
      SELECT c.*,
             COUNT(b.batch_id) as batch_count,
             COALESCE(SUM(CASE WHEN b.handover_status IN ('HANDED_OVER', 'VERIFIED_BY_RECYCLER') THEN 1 ELSE 0 END), 0) as handovers_count
      FROM collectors c
      LEFT JOIN batches b ON c.collector_id = b.collector_id
      GROUP BY c.collector_id
      ORDER BY c.rating DESC, c.total_collected_kg DESC
    `).all();

    res.json({ success: true, count: collectors.length, collectors });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

app.get('/api/collectors/:id', (req, res) => {
  try {
    const collector = db.prepare('SELECT * FROM collectors WHERE collector_id = ?').get(req.params.id);
    if (!collector) {
      return res.status(404).json({ success: false, message: 'Collector not found' });
    }
    const incentives = db.prepare('SELECT * FROM collector_incentives WHERE collector_id = ? ORDER BY timestamp DESC').all(req.params.id);
    res.json({ success: true, collector, incentives });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

app.get('/api/collectors/:id/incentives', (req, res) => {
  try {
    const rows = db.prepare('SELECT * FROM collector_incentives WHERE collector_id = ? ORDER BY timestamp DESC').all(req.params.id);
    res.json({ success: true, count: rows.length, incentives: rows });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// Seed sample batches if none exist
const initialBatchCount = db.prepare('SELECT COUNT(*) as count FROM batches').get().count;
if (initialBatchCount === 0) {
  const insertSample = db.prepare(`
    INSERT INTO batches (
      batch_id, collector_id, item_category, cpcb_category_code, ai_confidence,
      manual_override, estimated_weight_kg, item_count, latitude, longitude,
      location_address, timestamp, handover_status, recycler_id, qr_passcode,
      recycler_verification_hash, verified_weight_kg, created_at, updated_at
    ) VALUES (?, ?, ?, ?, 0.94, 0, ?, ?, 28.6692, 77.2764, 'Seelampur Depot', ?, ?, ?, '884920', ?, ?, ?, ?)
  `);

  insertSample.run('BATCH-INIT-001', 'KAB-DL-2024-001', 'Mobile Phones', 'ITEW15', 4.8, 3, Date.now() - 7200000, 'VERIFIED_BY_RECYCLER', 'REC-CPCB-001', 'EPR-CPCB-98FA71B043DE29', 4.8, Date.now(), Date.now());
  insertSample.run('BATCH-INIT-002', 'KAB-DL-2024-001', 'Circuit Boards / PCB', 'ITEW_PCB', 12.5, 8, Date.now() - 3600000, 'HANDED_OVER', 'REC-CPCB-001', null, null, Date.now(), Date.now());
  insertSample.run('BATCH-INIT-003', 'KAB-MH-2024-042', 'TV & Monitors', 'CEEW1', 22.0, 2, Date.now() - 1800000, 'COLLECTED', null, null, null, Date.now(), Date.now());
}

app.listen(PORT, '0.0.0.0', () => {
  console.log(`E-Waste Formalization REST API active on port ${PORT}`);
});
