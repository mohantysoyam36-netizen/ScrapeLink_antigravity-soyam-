let currentBatches = [];
let activeVerifyingBatch = null;

document.addEventListener('DOMContentLoaded', () => {
  loadDashboardData();
});

async function loadDashboardData() {
  await Promise.all([
    fetchMetrics(),
    fetchBatches()
  ]);
}

async function fetchMetrics() {
  try {
    const res = await fetch('/api/epr/metrics');
    const data = await res.json();
    if (!data.success) return;

    const s = data.summary;
    document.getElementById('kpiTotalCollected').textContent = `${s.totalCollectedKg} Kg`;
    document.getElementById('kpiTotalVerified').textContent = `${s.totalVerifiedKg} Kg`;
    document.getElementById('kpiCertCount').textContent = `${s.eprCertificatesIssued} CPCB Certificates Generated`;
    document.getElementById('kpiCollectors').textContent = s.formalizedKabadiwalas;
    document.getElementById('kpiPending').textContent = `${s.pendingVerificationBatches} Batches`;

    renderEprCategories(data.categoryBreakdown);
  } catch (err) {
    console.error('Failed to load metrics:', err);
  }
}

async function fetchBatches() {
  try {
    const filter = document.getElementById('statusFilter').value;
    const url = filter ? `/api/batches?status=${filter}` : '/api/batches';
    const res = await fetch(url);
    const data = await res.json();
    if (data.success) {
      currentBatches = data.batches;
      renderBatchesTable(currentBatches);
    }
  } catch (err) {
    console.error('Failed to fetch batches:', err);
  }
}

function renderBatchesTable(batches) {
  const tbody = document.getElementById('batchesTableBody');
  if (batches.length === 0) {
    tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="padding: 2rem; color: #64748b;">No batches found matching filter.</td></tr>`;
    return;
  }

  tbody.innerHTML = batches.map(b => {
    const dateStr = new Date(b.timestamp).toLocaleString();
    let badgeClass = 'badge-collected';
    let statusText = b.handover_status;
    let actionBtn = `<button class="btn btn-secondary btn-sm" onclick="viewPassport('${b.batch_id}')">Passport</button>`;

    if (b.handover_status === 'HANDED_OVER') {
      badgeClass = 'badge-handed';
      actionBtn = `<button class="btn btn-primary btn-sm" onclick="openVerifyModal('${b.batch_id}')">⚖️ Verify & Issue EPR</button>`;
    } else if (b.handover_status === 'VERIFIED_BY_RECYCLER') {
      badgeClass = 'badge-verified';
      actionBtn = `<button class="btn btn-secondary btn-sm" onclick="viewPassport('${b.batch_id}')">View Certificate</button>`;
    }

    return `
      <tr>
        <td><strong>${b.batch_id}</strong></td>
        <td>
          <div>${b.collector_id}</div>
          <small style="color:#64748b;">Formalized Kabadiwala</small>
        </td>
        <td>
          <strong>${b.cpcb_category_code}</strong>
          <div style="font-size: 0.8rem; color: #64748b;">${b.item_category}</div>
        </td>
        <td>${b.estimated_weight_kg} Kg (${b.item_count} units)</td>
        <td><small>${b.location_address || 'Delhi NCR'}</small></td>
        <td><small>${dateStr}</small></td>
        <td><span class="badge ${badgeClass}">${statusText}</span></td>
        <td>${actionBtn}</td>
      </tr>
    `;
  }).join('');
}

function renderEprCategories(breakdown) {
  const tbody = document.getElementById('eprTableBody');
  if (!breakdown || breakdown.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center">No categories recorded yet.</td></tr>`;
    return;
  }

  tbody.innerHTML = breakdown.map(cat => {
    return `
      <tr>
        <td><strong style="color:#0d47a1;">${cat.cpcb_category_code}</strong></td>
        <td>${cat.category_name}</td>
        <td>${cat.batch_count}</td>
        <td>${cat.estimated_kg.toFixed(1)} Kg</td>
        <td><strong style="color:#15803d;">${cat.verified_kg.toFixed(1)} Kg</strong></td>
        <td>
          <span class="badge ${cat.verified_kg > 0 ? 'badge-verified' : 'badge-collected'}">
            ${cat.verified_kg > 0 ? 'EPR Credits Active' : 'Pending Verification'}
          </span>
        </td>
      </tr>
    `;
  }).join('');
}

function filterBatches() {
  fetchBatches();
}

function switchTab(tabId) {
  document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

  event.target.classList.add('active');
  document.getElementById(tabId).classList.add('active');
}

function openVerifyModal(batchId) {
  const batch = currentBatches.find(b => b.batch_id === batchId);
  if (!batch) return;

  activeVerifyingBatch = batch;
  document.getElementById('modalBatchSummary').innerHTML = `
    <div><strong>Batch ID:</strong> ${batch.batch_id}</div>
    <div><strong>CPCB Category:</strong> ${batch.cpcb_category_code} — ${batch.item_category}</div>
    <div><strong>Collector:</strong> ${batch.collector_id}</div>
    <div><strong>Estimated Weight:</strong> ${batch.estimated_weight_kg} Kg</div>
    <div><strong>Collection GPS:</strong> ${batch.latitude}, ${batch.longitude} (${batch.location_address})</div>
    <div><strong>AI Confidence:</strong> ${(batch.ai_confidence * 100).toFixed(0)}%</div>
  `;

  document.getElementById('modalVerifiedWeight').value = batch.estimated_weight_kg;
  document.getElementById('verifyModal').style.display = 'flex';
}

function closeVerifyModal() {
  document.getElementById('verifyModal').style.display = 'none';
  activeVerifyingBatch = null;
}

async function confirmVerification() {
  if (!activeVerifyingBatch) return;

  const verifiedWeight = document.getElementById('modalVerifiedWeight').value;
  const inspectorName = document.getElementById('modalInspectorName').value;
  const notes = document.getElementById('modalNotes').value;

  try {
    const res = await fetch(`/api/batches/${activeVerifyingBatch.batch_id}/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        recyclerId: 'REC-CPCB-001',
        verifiedWeightKg: parseFloat(verifiedWeight),
        inspectorName,
        notes
      })
    });

    const result = await res.json();
    if (result.success) {
      alert(`✅ Verification Successful!\n\nEPR Verification Hash: ${result.verificationHash}\nWeight Verified: ${result.verifiedWeightKg} Kg\nCategory: ${result.cpcbCategory}`);
      closeVerifyModal();
      loadDashboardData();
    } else {
      alert('Error verifying batch: ' + result.message);
    }
  } catch (err) {
    console.error('Verification request failed:', err);
    alert('Failed to connect to backend server.');
  }
}

async function viewPassport(batchId) {
  document.getElementById('lookupInput').value = batchId;
  const tabBtn = document.querySelectorAll('.tab-btn')[2];
  tabBtn.click();
  lookupPassport();
}

async function lookupPassport() {
  const id = document.getElementById('lookupInput').value.trim();
  if (!id) return;

  try {
    const res = await fetch(`/api/batches/${id}`);
    const data = await res.json();
    const container = document.getElementById('passportResult');

    if (!data.success) {
      container.style.display = 'block';
      container.innerHTML = `<p style="color:#dc3545;">Batch passport not found.</p>`;
      return;
    }

    const b = data.passport;
    const v = data.verification;

    container.style.display = 'block';
    container.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: 1rem;">
        <div>
          <h3 style="font-size: 1.2rem; color: #1e293b;">Digital Material Passport: ${b.batch_id}</h3>
          <p style="color:#64748b; font-size: 0.85rem;">E-Waste (Management) Rules 2022 Traceable Manifest</p>
        </div>
        <span class="badge ${b.handover_status === 'VERIFIED_BY_RECYCLER' ? 'badge-verified' : 'badge-handed'}">${b.handover_status}</span>
      </div>

      <div style="display:grid; grid-template-columns: 1fr 1fr; gap: 1rem; font-size: 0.9rem;">
        <div style="background:#f8fafc; padding:1rem; border-radius:8px;">
          <h4 style="margin-bottom:0.5rem; color:#1e7e34;">Informal Collection Stage</h4>
          <p><strong>Collector:</strong> ${b.collector_name || b.collector_id} (${b.collector_phone || 'N/A'})</p>
          <p><strong>Item Category:</strong> ${b.item_category} (${b.cpcb_category_code})</p>
          <p><strong>AI Confidence:</strong> ${(b.ai_confidence * 100).toFixed(0)}% (MobileNetV2)</p>
          <p><strong>Estimated Weight:</strong> ${b.estimated_weight_kg} Kg (${b.item_count} units)</p>
          <p><strong>GPS Location:</strong> ${b.latitude}, ${b.longitude} (${b.location_address})</p>
          <p><strong>Collection Time:</strong> ${new Date(b.timestamp).toLocaleString()}</p>
        </div>

        <div style="background:#f8fafc; padding:1rem; border-radius:8px;">
          <h4 style="margin-bottom:0.5rem; color:#0d47a1;">Recycler Verification & EPR Stage</h4>
          <p><strong>Authorized Recycler:</strong> ${b.recycler_company || 'Eco-Greens Formal Hub'}</p>
          <p><strong>CPCB Reg No:</strong> ${b.cpcb_registration_number || 'CPCB/EW-REC/2023/DL-0081'}</p>
          <p><strong>Verified Weight:</strong> ${b.verified_weight_kg ? b.verified_weight_kg + ' Kg' : 'Pending Weighing'}</p>
          <p><strong>EPR Hash:</strong> <code style="word-break:break-all; color:#15803d;">${b.recycler_verification_hash || 'Pending Recycler Scan'}</code></p>
          ${v ? `<p><strong>Inspector:</strong> ${v.inspector_name}</p><p><strong>Notes:</strong> ${v.notes}</p>` : ''}
        </div>
      </div>
    `;
  } catch (err) {
    console.error('Passport lookup failed:', err);
  }
}
