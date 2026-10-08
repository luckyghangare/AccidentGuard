from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["Hospital Web Dashboard"])

HOSPITAL_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AccidentGuard - Hospital Emergency & Ambulance Dispatch Portal</title>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-primary: #0b0f19;
            --bg-secondary: #111827;
            --bg-card: rgba(17, 24, 39, 0.85);
            --border-card: rgba(255, 255, 255, 0.08);
            --danger-red: #ef4444;
            --danger-glow: rgba(239, 68, 68, 0.35);
            --warning-amber: #f59e0b;
            --safe-green: #10b981;
            --primary-blue: #2563eb;
            --primary-blue-hover: #1d4ed8;
            --teal-accent: #06b6d4;
            --text-primary: #f9fafb;
            --text-secondary: #9ca3af;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Plus Jakarta Sans', sans-serif;
        }

        body {
            background-color: var(--bg-primary);
            color: var(--text-primary);
            min-height: 100vh;
            background-image: 
                radial-gradient(circle at 10% 20%, rgba(37, 99, 235, 0.08) 0%, transparent 40%),
                radial-gradient(circle at 90% 80%, rgba(239, 68, 68, 0.08) 0%, transparent 40%);
        }

        /* Top Navigation */
        .navbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 16px 32px;
            background: rgba(17, 24, 39, 0.7);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--border-card);
            position: sticky;
            top: 0;
            z-index: 100;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .brand-icon {
            width: 42px;
            height: 42px;
            background: linear-gradient(135deg, #ef4444, #dc2626);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            box-shadow: 0 0 16px var(--danger-glow);
        }

        .brand-title {
            font-size: 20px;
            font-weight: 800;
            letter-spacing: -0.5px;
            color: #fff;
        }

        .brand-subtitle {
            font-size: 11px;
            color: var(--danger-red);
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .nav-right {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .hospital-badge {
            background: rgba(37, 99, 235, 0.15);
            border: 1px solid rgba(37, 99, 235, 0.3);
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 13px;
            font-weight: 600;
            color: #60a5fa;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .btn {
            padding: 8px 16px;
            border-radius: 8px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            text-decoration: none;
        }

        .btn-primary {
            background: var(--primary-blue);
            color: #fff;
        }
        .btn-primary:hover {
            background: var(--primary-blue-hover);
        }

        .btn-danger {
            background: linear-gradient(135deg, #ef4444, #dc2626);
            color: #fff;
            box-shadow: 0 4px 12px var(--danger-glow);
        }
        .btn-danger:hover {
            opacity: 0.92;
            transform: translateY(-1px);
        }

        .btn-outline {
            background: transparent;
            border: 1px solid var(--border-card);
            color: var(--text-secondary);
        }
        .btn-outline:hover {
            background: rgba(255, 255, 255, 0.05);
            color: #fff;
        }

        .btn-success {
            background: var(--safe-green);
            color: #fff;
        }

        /* Container */
        .container {
            max-width: 1320px;
            margin: 0 auto;
            padding: 28px 24px;
        }

        /* Stats Grid */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 16px;
            margin-bottom: 28px;
        }

        .stat-card {
            background: var(--bg-card);
            border: 1px solid var(--border-card);
            border-radius: 16px;
            padding: 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            backdrop-filter: blur(10px);
            transition: transform 0.2s ease;
        }
        .stat-card:hover {
            transform: translateY(-2px);
        }

        .stat-card.alert-active {
            border-color: var(--danger-red);
            box-shadow: 0 0 24px var(--danger-glow);
            animation: pulseGlow 2s infinite;
        }

        @keyframes pulseGlow {
            0%, 100% { box-shadow: 0 0 16px rgba(239, 68, 68, 0.3); }
            50% { box-shadow: 0 0 32px rgba(239, 68, 68, 0.6); }
        }

        .stat-info h4 {
            font-size: 13px;
            color: var(--text-secondary);
            font-weight: 600;
            margin-bottom: 6px;
        }

        .stat-info .stat-num {
            font-size: 28px;
            font-weight: 800;
            color: #fff;
        }

        .stat-icon {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
        }

        .bg-red { background: rgba(239, 68, 68, 0.15); color: var(--danger-red); }
        .bg-amber { background: rgba(245, 158, 11, 0.15); color: var(--warning-amber); }
        .bg-green { background: rgba(16, 185, 129, 0.15); color: var(--safe-green); }
        .bg-blue { background: rgba(37, 99, 235, 0.15); color: #60a5fa; }

        /* Section Header */
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }

        .section-title {
            font-size: 20px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .live-dot {
            width: 10px;
            height: 10px;
            background-color: var(--danger-red);
            border-radius: 50%;
            display: inline-block;
            box-shadow: 0 0 10px var(--danger-red);
            animation: blink 1.2s infinite;
        }

        @keyframes blink {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.3; }
        }

        /* Emergency Cards List */
        .alerts-list {
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .alert-card {
            background: var(--bg-card);
            border: 1px solid var(--border-card);
            border-radius: 18px;
            padding: 24px;
            backdrop-filter: blur(10px);
            position: relative;
            overflow: hidden;
            transition: all 0.2s ease;
        }

        .alert-card.status-pending {
            border-left: 6px solid var(--danger-red);
            background: linear-gradient(90deg, rgba(239, 68, 68, 0.08) 0%, rgba(17, 24, 39, 0.9) 30%);
        }

        .alert-card.status-dispatched {
            border-left: 6px solid var(--warning-amber);
        }

        .alert-card.status-admitted {
            border-left: 6px solid var(--safe-green);
            opacity: 0.85;
        }

        .alert-card-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 16px;
            flex-wrap: wrap;
            gap: 10px;
        }

        .victim-profile {
            display: flex;
            align-items: center;
            gap: 14px;
        }

        .victim-avatar {
            width: 50px;
            height: 50px;
            border-radius: 50%;
            background: rgba(255, 255, 255, 0.08);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
        }

        .victim-name {
            font-size: 18px;
            font-weight: 700;
            color: #fff;
        }

        .victim-phone {
            font-size: 14px;
            color: #60a5fa;
            text-decoration: none;
            display: flex;
            align-items: center;
            gap: 4px;
            margin-top: 2px;
        }
        .victim-phone:hover {
            text-decoration: underline;
        }

        .status-pill {
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }

        .pill-pending { background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.4); }
        .pill-dispatched { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.4); }
        .pill-admitted { background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.4); }

        /* Alert Details Grid */
        .alert-details {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            background: rgba(0, 0, 0, 0.25);
            padding: 16px;
            border-radius: 12px;
            margin-bottom: 18px;
        }

        .detail-item .label {
            font-size: 11px;
            color: var(--text-secondary);
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .detail-item .val {
            font-size: 14px;
            font-weight: 600;
            color: #f3f4f6;
            margin-top: 3px;
        }

        .severity-tag {
            color: #f87171;
            font-weight: 700;
        }

        /* Action Buttons */
        .alert-actions {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 12px;
            flex-wrap: wrap;
        }

        /* Modal */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.8);
            backdrop-filter: blur(8px);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.2s ease;
        }

        .modal-overlay.active {
            opacity: 1;
            pointer-events: all;
        }

        .modal-box {
            background: #111827;
            border: 1px solid var(--border-card);
            border-radius: 20px;
            width: 90%;
            max-width: 500px;
            padding: 28px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6);
        }

        .modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }

        .modal-title {
            font-size: 18px;
            font-weight: 700;
        }

        .form-group {
            margin-bottom: 16px;
        }

        .form-group label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: var(--text-secondary);
            margin-bottom: 6px;
        }

        .form-control {
            width: 100%;
            padding: 10px 14px;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid var(--border-card);
            border-radius: 10px;
            color: #fff;
            font-size: 14px;
            outline: none;
            transition: border 0.2s ease;
        }
        .form-control:focus {
            border-color: var(--primary-blue);
        }

        /* Empty State */
        .empty-state {
            background: var(--bg-card);
            border: 1px solid var(--border-card);
            border-radius: 18px;
            padding: 60px 20px;
            text-align: center;
        }

        .empty-icon {
            font-size: 54px;
            margin-bottom: 16px;
        }

        /* Auth Screen */
        .auth-container {
            max-width: 440px;
            margin: 60px auto;
            background: var(--bg-card);
            border: 1px solid var(--border-card);
            border-radius: 24px;
            padding: 36px;
            backdrop-filter: blur(12px);
            box-shadow: 0 24px 48px rgba(0, 0, 0, 0.5);
        }

        .auth-tabs {
            display: flex;
            border-bottom: 1px solid var(--border-card);
            margin-bottom: 24px;
        }

        .auth-tab {
            flex: 1;
            text-align: center;
            padding: 10px;
            font-weight: 600;
            color: var(--text-secondary);
            cursor: pointer;
            border-bottom: 2px solid transparent;
        }
        .auth-tab.active {
            color: var(--primary-blue);
            border-bottom-color: var(--primary-blue);
        }
    </style>
</head>
<body>

    <!-- Top Navbar -->
    <nav class="navbar">
        <div class="brand">
            <div class="brand-icon">🏥</div>
            <div>
                <div class="brand-title">AccidentGuard</div>
                <div class="brand-subtitle">Hospital Emergency Dispatch Command</div>
            </div>
        </div>

        <div class="nav-right" id="navAuthSection">
            <!-- Dynamic header depending on auth -->
        </div>
    </nav>

    <!-- Main App Container -->
    <div class="container" id="appRoot">
        <!-- Content will be rendered dynamically -->
    </div>

    <!-- Modal for Ambulance Dispatch -->
    <div class="modal-overlay" id="dispatchModal">
        <div class="modal-box">
            <div class="modal-header">
                <div class="modal-title">🚑 Dispatch Emergency Ambulance</div>
                <button class="btn btn-outline" style="padding: 4px 8px;" onclick="closeModal()">✕</button>
            </div>
            <form id="dispatchForm" onsubmit="submitDispatch(event)">
                <input type="hidden" id="modalAlertId">
                <div class="form-group">
                    <label>Ambulance Vehicle Number</label>
                    <input type="text" id="ambNumber" class="form-control" placeholder="e.g. MH-31-AMB-01" required value="MH-31-AMB-01">
                </div>
                <div class="form-group">
                    <label>Driver / Paramedic Name</label>
                    <input type="text" id="driverName" class="form-control" placeholder="e.g. Rajesh Sharma" required value="Rajesh Sharma">
                </div>
                <div class="form-group">
                    <label>Driver Contact Number</label>
                    <input type="text" id="driverPhone" class="form-control" placeholder="e.g. +91 9876543210" required value="+91 9876543210">
                </div>
                <div class="form-group">
                    <label>Estimated Time of Arrival (ETA in mins)</label>
                    <input type="number" id="etaMins" class="form-control" min="1" max="60" value="8" required>
                </div>
                <div class="form-group">
                    <label>Special Instructions / Medical Equipment</label>
                    <input type="text" id="dispatchNotes" class="form-control" placeholder="e.g. ICU ventilator unit & neck brace ready">
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px;">
                    <button type="button" class="btn btn-outline" onclick="closeModal()">Cancel</button>
                    <button type="submit" class="btn btn-danger">🚀 Confirm & Dispatch</button>
                </div>
            </form>
        </div>
    </div>

    <script>
        const API_BASE = window.location.origin + '/api/hospitals';
        let currentHospital = null;
        let token = localStorage.getItem('hospital_token');
        let refreshTimer = null;
        let knownPendingAlertIds = new Set();

        // Audio Alert Synth using Web Audio API
        function playEmergencySiren() {
            try {
                const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                const osc = audioCtx.createOscillator();
                const gain = audioCtx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(800, audioCtx.currentTime);
                osc.frequency.exponentialRampToValueAtTime(400, audioCtx.currentTime + 0.5);
                osc.frequency.exponentialRampToValueAtTime(800, audioCtx.currentTime + 1.0);
                gain.gain.setValueAtTime(0.15, audioCtx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 1.0);
                osc.connect(gain);
                gain.connect(audioCtx.destination);
                osc.start();
                osc.stop(audioCtx.currentTime + 1.0);
            } catch (e) {
                console.log('Audio alert blocked:', e);
            }
        }

        async function init() {
            if (token) {
                try {
                    const res = await fetch(`${API_BASE}/me`, {
                        headers: { 'Authorization': `Bearer ${token}` }
                    });
                    const json = await res.json();
                    if (json.status === 'success') {
                        currentHospital = json.data;
                        renderDashboard();
                        startLivePolling();
                        return;
                    }
                } catch (e) {
                    console.error('Auth check error:', e);
                }
            }
            renderAuthPage('login');
        }

        function renderAuthPage(tab = 'login') {
            stopLivePolling();
            document.getElementById('navAuthSection').innerHTML = '';
            
            const root = document.getElementById('appRoot');
            root.innerHTML = `
                <div class="auth-container">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="font-size: 38px; margin-bottom: 8px;">🏥</div>
                        <h2 style="font-weight: 800;">Hospital Command Center</h2>
                        <p style="font-size: 13px; color: var(--text-secondary); margin-top: 4px;">Sign in to receive live crash alerts & dispatch ambulances</p>
                    </div>

                    <div class="auth-tabs">
                        <div class="auth-tab ${tab === 'login' ? 'active' : ''}" onclick="renderAuthPage('login')">Hospital Login</div>
                        <div class="auth-tab ${tab === 'register' ? 'active' : ''}" onclick="renderAuthPage('register')">Register Hospital</div>
                    </div>

                    ${tab === 'login' ? `
                        <form id="loginForm" onsubmit="handleLogin(event)">
                            <div class="form-group">
                                <label>Hospital Email</label>
                                <input type="email" id="loginEmail" class="form-control" value="apex.hospital@accidentguard.com" required>
                            </div>
                            <div class="form-group">
                                <label>Password</label>
                                <input type="password" id="loginPassword" class="form-control" value="Hospital@123" required>
                            </div>
                            <button type="submit" class="btn btn-primary" style="width: 100%; padding: 12px; margin-top: 12px;">Login to Dashboard</button>
                            
                            <button type="button" class="btn btn-outline" style="width: 100%; padding: 10px; margin-top: 10px;" onclick="autoFillDemoHospital()">
                                ⚡ Auto-Fill Demo Hospital Credentials
                            </button>
                        </form>
                    ` : `
                        <form id="regForm" onsubmit="handleRegister(event)">
                            <div class="form-group">
                                <label>Hospital Name</label>
                                <input type="text" id="regName" class="form-control" placeholder="e.g. Apex Trauma Care Hospital" required>
                            </div>
                            <div class="form-group">
                                <label>Official Email</label>
                                <input type="email" id="regEmail" class="form-control" placeholder="emergency@hospital.com" required>
                            </div>
                            <div class="form-group">
                                <label>Emergency Phone Line</label>
                                <input type="text" id="regPhone" class="form-control" placeholder="+91 712 2548899" required>
                            </div>
                            <div class="form-group">
                                <label>Password</label>
                                <input type="password" id="regPassword" class="form-control" minlength="6" required>
                            </div>
                            <div class="form-group">
                                <label>Hospital Address / Area</label>
                                <input type="text" id="regAddress" class="form-control" placeholder="Central Avenue, Wardha Road, Nagpur" required>
                            </div>
                            <div class="form-group">
                                <label>Total Ambulances Available</label>
                                <input type="number" id="regAmbulances" class="form-control" min="1" max="100" value="5" required>
                            </div>
                            <button type="submit" class="btn btn-primary" style="width: 100%; padding: 12px; margin-top: 12px;">Register Hospital</button>
                        </form>
                    `}
                </div>
            `;
        }

        async function handleLogin(e) {
            e.preventDefault();
            const email = document.getElementById('loginEmail').value.trim();
            const password = document.getElementById('loginPassword').value;

            try {
                const res = await fetch(`${API_BASE}/login`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password })
                });
                const json = await res.json();
                if (json.status === 'success') {
                    token = json.data.access_token;
                    currentHospital = json.data.hospital;
                    localStorage.setItem('hospital_token', token);
                    renderDashboard();
                    startLivePolling();
                } else {
                    alert(json.message || 'Login failed');
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
            }
        }

        async function handleRegister(e) {
            e.preventDefault();
            const data = {
                name: document.getElementById('regName').value.trim(),
                email: document.getElementById('regEmail').value.trim(),
                phone_number: document.getElementById('regPhone').value.trim(),
                password: document.getElementById('regPassword').value,
                address: document.getElementById('regAddress').value.trim(),
                total_ambulances: parseInt(document.getElementById('regAmbulances').value) || 5
            };

            try {
                const res = await fetch(`${API_BASE}/register`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                });
                const json = await res.json();
                if (json.status === 'success') {
                    token = json.data.access_token;
                    currentHospital = json.data.hospital;
                    localStorage.setItem('hospital_token', token);
                    alert('Hospital registered successfully!');
                    renderDashboard();
                    startLivePolling();
                } else {
                    alert(json.message || 'Registration failed');
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
            }
        }

        function autoFillDemoHospital() {
            document.getElementById('loginEmail').value = 'apex.hospital@accidentguard.com';
            document.getElementById('loginPassword').value = 'Hospital@123';
        }

        function logout() {
            token = null;
            currentHospital = null;
            localStorage.removeItem('hospital_token');
            renderAuthPage('login');
        }

        function renderDashboard() {
            document.getElementById('navAuthSection').innerHTML = `
                <div class="hospital-badge">
                    <span>🏥</span>
                    <span>${currentHospital.name}</span>
                </div>
                <button class="btn btn-outline" onclick="logout()">Logout</button>
            `;

            const root = document.getElementById('appRoot');
            root.innerHTML = `
                <!-- Stats Grid -->
                <div class="stats-grid" id="statsContainer">
                    <div class="stat-card">
                        <div class="stat-info">
                            <h4>PENDING EMERGENCIES</h4>
                            <div class="stat-num" id="statPending">0</div>
                        </div>
                        <div class="stat-icon bg-red">🚨</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <h4>AMBULANCES DISPATCHED</h4>
                            <div class="stat-num" id="statDispatched">0</div>
                        </div>
                        <div class="stat-icon bg-amber">🚑</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <h4>PATIENTS ADMITTED</h4>
                            <div class="stat-num" id="statAdmitted">0</div>
                        </div>
                        <div class="stat-icon bg-green">🏥</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <h4>AVAILABLE FLEET</h4>
                            <div class="stat-num" id="statAmbulances">${currentHospital.available_ambulances}/${currentHospital.total_ambulances}</div>
                        </div>
                        <div class="stat-icon bg-blue">🚐</div>
                    </div>
                </div>

                <!-- Feed Header -->
                <div class="section-header">
                    <div class="section-title">
                        <span class="live-dot"></span>
                        <span>Live Emergency Incident Feed</span>
                    </div>
                    <div style="display: flex; gap: 10px;">
                        <button class="btn btn-outline" onclick="triggerTestEmergency()">🚨 Simulate Live Crash</button>
                        <button class="btn btn-primary" onclick="loadAlerts()">🔄 Refresh Feed</button>
                    </div>
                </div>

                <!-- Alerts List -->
                <div class="alerts-list" id="alertsList">
                    <div class="empty-state">
                        <div class="empty-icon">⏳</div>
                        <h3>Connecting to Emergency Network...</h3>
                    </div>
                </div>
            `;

            loadStats();
            loadAlerts();
        }

        async function loadStats() {
            if (!token) return;
            try {
                const res = await fetch(`${API_BASE}/stats`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const json = await res.json();
                if (json.status === 'success') {
                    const d = json.data;
                    document.getElementById('statPending').innerText = d.pending_emergencies;
                    document.getElementById('statDispatched').innerText = d.active_dispatches;
                    document.getElementById('statAdmitted').innerText = d.admitted_patients;
                    document.getElementById('statAmbulances').innerText = `${d.available_ambulances}/${d.total_ambulances}`;

                    const pendingCard = document.querySelector('.stat-card');
                    if (d.pending_emergencies > 0) {
                        pendingCard.classList.add('alert-active');
                    } else {
                        pendingCard.classList.remove('alert-active');
                    }
                }
            } catch (e) {
                console.error(e);
            }
        }

        async function loadAlerts() {
            if (!token) return;
            try {
                const res = await fetch(`${API_BASE}/alerts`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const json = await res.json();
                if (json.status === 'success') {
                    const list = json.data;
                    renderAlertsList(list);

                    // Check for new pending alerts to sound siren
                    let hasNewPending = false;
                    list.forEach(a => {
                        if (a.status === 'PENDING' && !knownPendingAlertIds.has(a.id)) {
                            hasNewPending = true;
                            knownPendingAlertIds.add(a.id);
                        }
                    });

                    if (hasNewPending) {
                        playEmergencySiren();
                    }
                }
            } catch (e) {
                console.error(e);
            }
        }

        function renderAlertsList(alerts) {
            const container = document.getElementById('alertsList');
            if (!container) return;

            if (alerts.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <div class="empty-icon">🛡️</div>
                        <h3>No Active Accidents Reported</h3>
                        <p style="color: var(--text-secondary); margin-top: 6px; font-size: 14px;">All vehicles monitored by AccidentGuard are currently safe.</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = alerts.map(a => {
                let statusClass = 'status-pending';
                let pillClass = 'pill-pending';
                let statusText = '🚨 PENDING DISPATCH';

                if (a.status === 'DISPATCHED') {
                    statusClass = 'status-dispatched';
                    pillClass = 'pill-dispatched';
                    statusText = `🚑 EN ROUTE (ETA: ${a.eta_minutes || 10}m)`;
                } else if (a.status === 'ADMITTED') {
                    statusClass = 'status-admitted';
                    pillClass = 'pill-admitted';
                    statusText = '🏥 PATIENT ADMITTED';
                }

                return `
                    <div class="alert-card ${statusClass}">
                        <div class="alert-card-header">
                            <div class="victim-profile">
                                <div class="victim-avatar">👤</div>
                                <div>
                                    <div class="victim-name">${a.victim.user_name}</div>
                                    <a href="tel:${a.victim.user_phone}" class="victim-phone">📞 ${a.victim.user_phone}</a>
                                </div>
                            </div>
                            <div>
                                <span class="status-pill ${pillClass}">${statusText}</span>
                            </div>
                        </div>

                        <div class="alert-details">
                            <div class="detail-item">
                                <div class="label">🚗 Vehicle Info</div>
                                <div class="val">${a.victim.vehicle_name} (${a.victim.vehicle_number})</div>
                            </div>
                            <div class="detail-item">
                                <div class="label">💥 Impact & Damage</div>
                                <div class="val severity-tag">${a.impact_g}g · ${a.damage_severity}</div>
                            </div>
                            <div class="detail-item">
                                <div class="label">📍 Crash Location</div>
                                <div class="val">${a.latitude.toFixed(4)}, ${a.longitude.toFixed(4)}</div>
                            </div>
                            <div class="detail-item">
                                <div class="label">🕒 Crash Time</div>
                                <div class="val">${a.date_str} at ${a.time_str}</div>
                            </div>
                        </div>

                        ${a.status === 'DISPATCHED' ? `
                            <div style="background: rgba(245, 158, 11, 0.1); border: 1px solid rgba(245, 158, 11, 0.2); padding: 12px; border-radius: 10px; margin-bottom: 16px; font-size: 13px;">
                                🚑 <strong>Dispatched:</strong> ${a.ambulance_number} · Driver: ${a.driver_name} (${a.driver_phone}) · ETA: ${a.eta_minutes} mins
                                ${a.notes ? `<div style="color: var(--text-secondary); margin-top: 4px;">📝 ${a.notes}</div>` : ''}
                            </div>
                        ` : ''}

                        <div class="alert-actions">
                            <a href="${a.maps_url}" target="_blank" class="btn btn-outline">
                                🗺️ Open Live Map Navigation
                            </a>

                            ${a.status === 'PENDING' ? `
                                <button class="btn btn-danger" onclick="openDispatchModal(${a.id})">
                                    🚑 Dispatch Ambulance Now
                                </button>
                            ` : ''}

                            ${a.status === 'DISPATCHED' ? `
                                <button class="btn btn-success" onclick="markAdmitted(${a.id})">
                                    ✅ Patient Admitted / Recovered
                                </button>
                            ` : ''}
                        </div>
                    </div>
                `;
            }).join('');
        }

        function openDispatchModal(alertId) {
            document.getElementById('modalAlertId').value = alertId;
            document.getElementById('dispatchModal').classList.add('active');
        }

        function closeModal() {
            document.getElementById('dispatchModal').classList.remove('active');
        }

        async function submitDispatch(e) {
            e.preventDefault();
            const alertId = document.getElementById('modalAlertId').value;
            const data = {
                ambulance_number: document.getElementById('ambNumber').value.trim(),
                driver_name: document.getElementById('driverName').value.trim(),
                driver_phone: document.getElementById('driverPhone').value.trim(),
                eta_minutes: parseInt(document.getElementById('etaMins').value) || 10,
                notes: document.getElementById('dispatchNotes').value.trim()
            };

            try {
                const res = await fetch(`${API_BASE}/alerts/${alertId}/dispatch`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    },
                    body: JSON.stringify(data)
                });
                const json = await res.json();
                if (json.status === 'success') {
                    closeModal();
                    loadStats();
                    loadAlerts();
                } else {
                    alert(json.message || 'Dispatch failed');
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
            }
        }

        async function markAdmitted(alertId) {
            if (!confirm('Confirm patient has arrived at hospital and is admitted?')) return;
            try {
                const res = await fetch(`${API_BASE}/alerts/${alertId}/admit`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    },
                    body: JSON.stringify({ notes: 'Patient admitted into emergency trauma ward' })
                });
                const json = await res.json();
                if (json.status === 'success') {
                    loadStats();
                    loadAlerts();
                }
            } catch (err) {
                alert('Error: ' + err.message);
            }
        }

        async function triggerTestEmergency() {
            try {
                const res = await fetch('/api/accidents/report', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        vehicle_id: 1,
                        latitude: 21.1458 + (Math.random() - 0.5) * 0.02,
                        longitude: 79.0882 + (Math.random() - 0.5) * 0.02,
                        impact_g: (4.5 + Math.random() * 4.0).toFixed(1)
                    })
                });
                const json = await res.json();
                if (json.status === 'success') {
                    loadStats();
                    loadAlerts();
                }
            } catch (err) {
                alert('Simulation error: ' + err.message);
            }
        }

        function startLivePolling() {
            stopLivePolling();
            refreshTimer = setInterval(() => {
                loadStats();
                loadAlerts();
            }, 4000);
        }

        function stopLivePolling() {
            if (refreshTimer) {
                clearInterval(refreshTimer);
                refreshTimer = null;
            }
        }

        // Initialize on load
        window.addEventListener('DOMContentLoaded', init);
    </script>
</body>
</html>
"""

@router.get("/hospital", response_class=HTMLResponse)
@router.get("/hospital/dashboard", response_class=HTMLResponse)
@router.get("/hospital/login", response_class=HTMLResponse)
@router.get("/hospital/register", response_class=HTMLResponse)
def serve_hospital_portal():
    return HTMLResponse(content=HOSPITAL_HTML, status_code=200)
