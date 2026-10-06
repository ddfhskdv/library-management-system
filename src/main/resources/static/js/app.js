// Library Management System - Frontend App Logic
const API_BASE = '/api';
const nativeFetch = window.fetch.bind(window);

window.fetch = async (input, init = {}) => {
    const credentials = sessionStorage.getItem('libraryCredentials');
    const headers = new Headers(init.headers || {});
    if (credentials && String(input).startsWith(API_BASE)) {
        headers.set('Authorization', `Basic ${credentials}`);
    }
    const response = await nativeFetch(input, { ...init, headers });
    if (response.status === 401 && String(input).startsWith(API_BASE)) {
        const modal = document.getElementById('authModal');
        if (modal) modal.classList.remove('hidden');
        const error = document.getElementById('authError');
        if (error) {
            error.innerText = credentials ? 'Sign in failed. Check your username and password.' : 'Please sign in to continue.';
            error.classList.remove('hidden');
        }
    }
    return response;
};

let allBooksCache = [];
let allMembersCache = [];
let allTransactionsCache = [];
const TABLE_PAGE_SIZE = 10;
let transactionPage = 1;
let dailyFineRate = 5;

// Initialize application on load
document.addEventListener('DOMContentLoaded', () => {
    const authForm = document.getElementById('authForm');
    authForm?.addEventListener('submit', async event => {
        event.preventDefault();
        const username = document.getElementById('authUsername').value.trim();
        const password = document.getElementById('authPassword').value;
        const encoded = btoa(`${username}:${password}`);
        sessionStorage.setItem('libraryCredentials', encoded);
        const response = await nativeFetch(`${API_BASE}/dashboard/stats`, {
            headers: { Authorization: `Basic ${encoded}` }
        });
        if (!response.ok) {
            sessionStorage.removeItem('libraryCredentials');
            document.getElementById('authError').innerText = 'Sign in failed. Check your username and password.';
            document.getElementById('authError').classList.remove('hidden');
            return;
        }
        document.getElementById('authModal').classList.add('hidden');
        document.getElementById('authError').classList.add('hidden');
        document.getElementById('authButton').innerText = `Sign out (${username})`;
        fetchDashboardStats();
        fetchAllBooks();
        fetchAllMembers();
        fetchAllTransactions();
        loadCategories();
    });
    const savedCredentials = sessionStorage.getItem('libraryCredentials');
    const authButton = document.getElementById('authButton');
    if (savedCredentials && authButton) {
        authButton.innerText = 'Sign out';
    }
    // Dark mode persistence (Step 15)
    if (localStorage.getItem('darkMode') === 'dark') {
        document.documentElement.classList.add('dark');
    }
    updateDarkModeIcon();

    // Set default return date in return form to today
    const today = new Date().toISOString().split('T')[0];
    const returnDateInput = document.getElementById('customReturnDate');
    if (returnDateInput) returnDateInput.value = today;

    // Load initial data
    fetchDashboardStats();
    fetchAllBooks();
    fetchAllMembers();
    fetchAllTransactions();
    loadCategories();
});

function signOut() {
    sessionStorage.removeItem('libraryCredentials');
    window.location.reload();
}

async function showMemberQr(id) {
    try {
        const response = await fetch(`${API_BASE}/members/${id}/qr.png`);
        if (!response.ok) throw new Error('Could not generate this student QR code.');
        const member = allMembersCache.find(item => item.id === id);
        const image = document.getElementById('bookCodeImage');
        if (image.dataset.objectUrl) URL.revokeObjectURL(image.dataset.objectUrl);
        const objectUrl = URL.createObjectURL(await response.blob());
        image.src = objectUrl;
        image.dataset.objectUrl = objectUrl;
        document.getElementById('bookCodeTitle').innerText = 'Student ID QR code';
        document.getElementById('bookCodeValue').innerText = member?.membershipNumber || '';
        openModal('bookCodeModal');
    } catch (error) {
        showToast(error.message, true);
    }
}

// ============================================================================
// DARK MODE (Step 15)
// ============================================================================
function toggleDarkMode() {
    document.documentElement.classList.toggle('dark');
    const isDark = document.documentElement.classList.contains('dark');
    localStorage.setItem('darkMode', isDark ? 'dark' : 'light');
    updateDarkModeIcon();
}

function updateDarkModeIcon() {
    const icon = document.getElementById('darkModeIcon');
    if (!icon) return;
    if (document.documentElement.classList.contains('dark')) {
        icon.className = 'fa-solid fa-sun';
    } else {
        icon.className = 'fa-solid fa-moon';
    }
}

// ============================================================================
// LOADING SPINNER (Step 16)
// ============================================================================
function showSpinner() {
    const el = document.getElementById('loadingOverlay');
    if (el) {
        el.classList.remove('hidden');
        el.classList.add('flex');
    }
}

function hideSpinner() {
    const el = document.getElementById('loadingOverlay');
    if (el) {
        el.classList.add('hidden');
        el.classList.remove('flex');
    }
}

// ============================================================================
// CUSTOM CONFIRM DIALOG (Step 17)
// ============================================================================
let pendingConfirmCallback = null;

function showConfirm(title, message, onConfirm) {
    document.getElementById('confirmTitle').innerText = title;
    document.getElementById('confirmMessage').innerText = message;
    pendingConfirmCallback = onConfirm;
    openModal('confirmModal');
}

function confirmAction() {
    closeModal('confirmModal');
    if (pendingConfirmCallback) {
        pendingConfirmCallback();
    }
    pendingConfirmCallback = null;
}

// ============================================================================
// INLINE FIELD VALIDATION (Step 19)
// ============================================================================
function showFieldError(inputId, message) {
    const input = document.getElementById(inputId);
    if (!input) return;
    let errEl = input.parentElement.querySelector('.field-error');
    if (!errEl) {
        errEl = document.createElement('p');
        errEl.className = 'field-error text-rose-500 text-xs mt-1';
        input.parentElement.appendChild(errEl);
    }
    errEl.innerText = message;
}

function clearFieldErrors(formId) {
    const form = document.getElementById(formId);
    if (!form) return;
    form.querySelectorAll('.field-error').forEach(el => el.remove());
}

// ============================================================================
// TAB NAVIGATION
// ============================================================================
function switchTab(tabName) {
    const tabs = ['dashboard', 'books', 'members', 'scan', 'transactions'];
    if (tabName !== 'scan') stopScanner();
    tabs.forEach(t => {
        const section = document.getElementById(`tab-${t}`);
        const navBtn = document.getElementById(`nav-${t}`);
        if (t === tabName) {
            if (section) section.classList.remove('hidden');
            if (navBtn) {
                navBtn.classList.add('active-tab');
                navBtn.classList.remove('text-slate-600', 'hover:bg-slate-100');
            }
        } else {
            if (section) section.classList.add('hidden');
            if (navBtn) {
                navBtn.classList.remove('active-tab');
                navBtn.classList.add('text-slate-600', 'hover:bg-slate-100');
            }
        }
    });

    if (tabName === 'dashboard') {
        fetchDashboardStats();
        loadDashboardRecentTable();
        updateOutstandingFines();
    } else if (tabName === 'books') {
        fetchAllBooks();
    } else if (tabName === 'members') {
        fetchAllMembers();
    } else if (tabName === 'scan') {
        resetScanSession();
    } else if (tabName === 'transactions') {
        populateIssueDropdowns();
        populateReturnDropdown();
        fetchAllTransactions();
        loadReservations();
    }
}

let scannerStream = null;
let scannerFrame = null;
let barcodeDetector = null;
let scannedMember = null;
let scannedBook = null;
let scannedLoans = [];

function setScanStatus(message, isError = false) {
    const status = document.getElementById('scanStatus');
    if (!status) return;
    status.innerText = message;
    status.className = `text-sm mt-3 ${isError ? 'text-rose-300' : 'text-slate-200'}`;
}

async function startScanner() {
    if (!('BarcodeDetector' in window)) {
        setScanStatus('This browser does not provide camera QR/barcode scanning. Enter the printed ID manually.', true);
        return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
        setScanStatus('Camera access is unavailable. Use localhost/HTTPS or enter the printed ID manually.', true);
        return;
    }
    try {
        const supported = await window.BarcodeDetector.getSupportedFormats();
        const formats = ['qr_code', 'code_128', 'ean_13', 'ean_8', 'upc_a', 'upc_e']
            .filter(format => supported.includes(format));
        if (formats.length === 0) {
            setScanStatus('No supported QR/barcode formats were found. Use manual ID entry.', true);
            return;
        }
        barcodeDetector = new window.BarcodeDetector({ formats });
        scannerStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: { ideal: 'environment' } },
            audio: false
        });
        const video = document.getElementById('scannerVideo');
        video.srcObject = scannerStream;
        await video.play();
        setScanStatus('Camera is ready. Point it at the next QR code or barcode.');
        scanCameraFrame();
    } catch (error) {
        stopScanner();
        setScanStatus(error.name === 'NotAllowedError'
            ? 'Camera permission was denied. Allow camera access or enter an ID manually.'
            : `Could not start the camera: ${error.message}`, true);
    }
}

async function scanCameraFrame() {
    if (!scannerStream || !barcodeDetector) return;
    const video = document.getElementById('scannerVideo');
    if (video.readyState >= HTMLMediaElement.HAVE_ENOUGH_DATA) {
        try {
            const codes = await barcodeDetector.detect(video);
            if (codes.length && codes[0].rawValue) {
                stopScanner();
                document.getElementById('scanCodeInput').value = codes[0].rawValue;
                await processScannedCode(codes[0].rawValue);
                return;
            }
        } catch (error) {
            stopScanner();
            setScanStatus(`Camera scan failed: ${error.message}`, true);
            return;
        }
    }
    scannerFrame = requestAnimationFrame(scanCameraFrame);
}

function stopScanner() {
    if (scannerFrame !== null) cancelAnimationFrame(scannerFrame);
    scannerFrame = null;
    if (scannerStream) scannerStream.getTracks().forEach(track => track.stop());
    scannerStream = null;
    const video = document.getElementById('scannerVideo');
    if (video) video.srcObject = null;
}

function resetScanSession() {
    stopScanner();
    scannedMember = null;
    scannedBook = null;
    scannedLoans = [];
    document.getElementById('scanStudentDetails').innerText = 'Scan or enter the student\'s ID.';
    document.getElementById('scanBookDetails').innerText = 'Scan or enter the book\'s QR/barcode ID.';
    document.getElementById('confirmScanIssue').classList.add('hidden');
    document.getElementById('confirmScanReturn').classList.add('hidden');
    document.getElementById('scanReturnLoanGroup').classList.add('hidden');
    document.getElementById('scanReceipt').classList.add('hidden');
    document.getElementById('scanCodeInput').value = '';
    const issueMode = document.getElementById('scanMode').value === 'issue';
    document.getElementById('scanLoanDaysGroup').classList.toggle('hidden', !issueMode);
    setScanStatus(issueMode
        ? 'Scan or enter the student ID first, then scan the book code.'
        : 'Scan or enter a book code to find the active loan.');
}

function resolveManualScan() {
    const code = document.getElementById('scanCodeInput').value.trim();
    if (!code) {
        setScanStatus('Enter or scan an ID first.', true);
        return;
    }
    processScannedCode(code);
}

async function processScannedCode(code) {
    setScanStatus(`Looking up ${code}…`);
    if (document.getElementById('scanMode').value === 'issue') {
        if (!scannedMember) {
            try {
                const response = await fetch(`${API_BASE}/members/lookup?code=${encodeURIComponent(code)}`);
                const result = await response.json();
                if (!response.ok) throw new Error(result.message || 'Student ID not found.');
                scannedMember = result;
                document.getElementById('scanStudentDetails').innerText =
                    `${result.name} · ${result.membershipNumber} · ${result.active ? 'Active' : 'Inactive'}`;
                if (!result.active) throw new Error('This student account is inactive and cannot borrow books.');
                setScanStatus('Student verified. Now scan or enter the book ID.');
            } catch (error) {
                scannedMember = null;
                setScanStatus(error.message, true);
            }
            return;
        }
        await lookupBookForIssue(code);
        return;
    }
    await lookupBookForReturn(code);
}

async function lookupBookForIssue(code) {
    try {
        const response = await fetch(`${API_BASE}/books/lookup?code=${encodeURIComponent(code)}`);
        const book = await response.json();
        if (!response.ok) throw new Error(book.message || 'Book ID not found.');
        if (!book.available || book.availableCopies < 1) throw new Error('This book has no available copies. Add the student to the reservation waitlist instead.');
        scannedBook = book;
        document.getElementById('scanBookDetails').innerText =
            `${book.title} · ${book.catalogCode} · ${book.availableCopies} available`;
        document.getElementById('confirmScanIssue').classList.remove('hidden');
        setScanStatus('Both IDs are verified. Confirm the issue to create the loan.');
    } catch (error) {
        scannedBook = null;
        document.getElementById('confirmScanIssue').classList.add('hidden');
        setScanStatus(error.message, true);
    }
}

async function lookupBookForReturn(code) {
    try {
        const bookResponse = await fetch(`${API_BASE}/books/lookup?code=${encodeURIComponent(code)}`);
        const book = await bookResponse.json();
        if (!bookResponse.ok) throw new Error(book.message || 'Book ID not found.');
        const loansResponse = await fetch(`${API_BASE}/transactions/active`);
        if (!loansResponse.ok) throw new Error('Could not load active loans.');
        scannedLoans = (await loansResponse.json()).filter(loan => loan.book.id === book.id);
        if (scannedLoans.length === 0) throw new Error('There is no active loan for this book.');
        scannedBook = book;
        document.getElementById('scanBookDetails').innerText = `${book.title} · ${book.catalogCode}`;
        const loanGroup = document.getElementById('scanReturnLoanGroup');
        const loanSelect = document.getElementById('scanReturnLoan');
        loanSelect.innerHTML = scannedLoans.map(loan =>
            `<option value="${loan.id}">${escapeHtml(loan.member.name)} · ${escapeHtml(loan.member.membershipNumber)} · due ${loan.dueDate}</option>`
        ).join('');
        loanGroup.classList.toggle('hidden', scannedLoans.length === 1);
        document.getElementById('scanStudentDetails').innerText = scannedLoans.length === 1
            ? `${scannedLoans[0].member.name} · ${scannedLoans[0].member.membershipNumber}`
            : `${scannedLoans.length} active loans found; select the borrower.`;
        document.getElementById('confirmScanReturn').classList.remove('hidden');
        setScanStatus('Book and active loan are matched. Confirm the return to calculate any fine.');
    } catch (error) {
        scannedBook = null;
        scannedLoans = [];
        document.getElementById('confirmScanReturn').classList.add('hidden');
        setScanStatus(error.message, true);
    }
}

async function confirmScannedIssue() {
    if (!scannedMember || !scannedBook) return;
    const loanDays = Number(document.getElementById('scanLoanDays').value);
    if (!Number.isInteger(loanDays) || loanDays < 1 || loanDays > 60) {
        setScanStatus('Loan duration must be between 1 and 60 days.', true);
        return;
    }
    try {
        const response = await fetch(`${API_BASE}/transactions/issue`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ memberId: scannedMember.id, bookId: scannedBook.id, loanDays })
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || 'Book issue failed.');
        showScanReceipt(`<strong>Book issued</strong><br>Student: ${escapeHtml(result.member.name)} (${escapeHtml(result.member.membershipNumber)})<br>Book: ${escapeHtml(result.book.title)} (${escapeHtml(result.book.catalogCode)})<br>Issue date: ${result.issueDate}<br>Due date: ${result.dueDate}<br>Status: Issued`);
        setScanStatus('Issue recorded successfully.');
        fetchDashboardStats();
        fetchAllTransactions();
        fetchAllBooks();
    } catch (error) {
        setScanStatus(error.message, true);
    }
}

async function confirmScannedReturn() {
    if (!scannedBook || !scannedLoans.length) return;
    const transactionId = Number(document.getElementById('scanReturnLoan').value || scannedLoans[0].id);
    try {
        const response = await fetch(`${API_BASE}/transactions/return`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transactionId })
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || 'Book return failed.');
        showScanReceipt(`<strong>Book returned</strong><br>Student: ${escapeHtml(result.memberName)}<br>Book: ${escapeHtml(result.bookTitle)}<br>Return date: ${result.returnDate}<br>Late days: ${result.lateDays}<br>Fine due: ₹${Number(result.fineAmount).toFixed(2)}`);
        setScanStatus('Return recorded successfully.');
        fetchDashboardStats();
        fetchAllTransactions();
        fetchAllBooks();
    } catch (error) {
        setScanStatus(error.message, true);
    }
}

function showScanReceipt(html) {
    const receipt = document.getElementById('scanReceipt');
    receipt.innerHTML = html;
    receipt.classList.remove('hidden');
    document.getElementById('confirmScanIssue').classList.add('hidden');
    document.getElementById('confirmScanReturn').classList.add('hidden');
}

// ============================================================================
// DASHBOARD METRICS
// ============================================================================
async function fetchDashboardStats() {
    try {
        const res = await fetch(`${API_BASE}/dashboard/stats`);
        if (!res.ok) throw new Error('Failed to fetch dashboard statistics');
        const data = await res.json();

        document.getElementById('stat-total-books').innerText = data.totalBooks.toLocaleString();
        document.getElementById('stat-available-books').innerText = data.availableBooks.toLocaleString();
        document.getElementById('stat-issued-books').innerText = data.issuedBooks.toLocaleString();
        document.getElementById('stat-members').innerText = data.totalMembers.toLocaleString();
        document.getElementById('stat-overdue').innerText = data.overdueBooks.toLocaleString();
        document.getElementById('stat-fines').innerText = `₹${data.totalFinesCollected.toFixed(2)}`;
        document.getElementById('stat-outstanding-fines').innerText = `₹${data.outstandingFines.toFixed(2)}`;
        dailyFineRate = Number(data.dailyFineRate);
        document.getElementById('fineRateLabel').innerText = dailyFineRate.toFixed(2);
        document.getElementById('fineRatePreviewLabel').innerText = dailyFineRate.toFixed(2);

        if (data.totalBooks > 0) {
            const availPct = Math.round((data.availableBooks / data.totalBooks) * 100);
            const issuedPct = Math.round((data.issuedBooks / data.totalBooks) * 100);
            const barAvail = document.getElementById('bar-available');
            const barIssued = document.getElementById('bar-issued');
            if (barAvail) barAvail.style.width = `${Math.min(availPct, 100)}%`;
            if (barIssued) barIssued.style.width = `${Math.min(issuedPct, 100)}%`;
        }

        loadDashboardRecentTable();
        loadPopularBooks(); // Step 32
    } catch (err) {
        console.error('Error loading dashboard stats:', err);
    }
}

async function loadDashboardRecentTable() {
    try {
        const res = await fetch(`${API_BASE}/transactions/active`);
        if (!res.ok) return;
        const list = await res.json();
        const tbody = document.getElementById('dashboard-recent-table');
        if (!tbody) return;

        if (list.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="px-6 py-6 text-center text-slate-400">No active book loans.</td></tr>`;
            return;
        }

        const today = new Date();
        tbody.innerHTML = list.slice(0, 6).map(tx => {
            const due = new Date(tx.dueDate);
            const isOverdue = today > due;
            return `
                <tr class="hover:bg-slate-50 transition">
                    <td class="px-6 py-3.5 font-medium text-slate-900">${escapeHtml(tx.book.title)}</td>
                    <td class="px-6 py-3.5">${escapeHtml(tx.member.name)} (${escapeHtml(tx.member.membershipNumber)})</td>
                    <td class="px-6 py-3.5 text-xs text-slate-500">${tx.issueDate}</td>
                    <td class="px-6 py-3.5 text-xs font-semibold ${isOverdue ? 'text-rose-600' : 'text-slate-700'}">${tx.dueDate}</td>
                    <td class="px-6 py-3.5">
                        <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${isOverdue ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800'}">
                            ${isOverdue ? 'Overdue' : 'Active Loan'}
                        </span>
                    </td>
                    <td class="px-6 py-3.5 text-right">
                        <button onclick="quickReturn(${tx.id})" class="text-xs text-blue-600 hover:text-blue-800 font-semibold underline">
                            Return
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        console.error('Error loading recent active loans:', err);
    }
}

// ============================================================================
// OVERDUE DETAIL PANEL (Step 31)
// ============================================================================
let overdueDetailLoaded = false;

function toggleOverduePanel() {
    const content = document.getElementById('overdueDetailContent');
    const chevron = document.getElementById('overdueChevron');
    if (content.classList.contains('hidden')) {
        content.classList.remove('hidden');
        chevron.classList.add('rotate-180');
        if (!overdueDetailLoaded) {
            loadOverdueDetail();
            overdueDetailLoaded = true;
        }
    } else {
        content.classList.add('hidden');
        chevron.classList.remove('rotate-180');
    }
}

async function loadOverdueDetail() {
    try {
        const res = await fetch(`${API_BASE}/transactions/overdue`);
        if (!res.ok) throw new Error();
        const list = await res.json();
        const tbody = document.getElementById('overdueDetailTable');
        const today = new Date();
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-slate-400">No overdue books.</td></tr>';
            return;
        }
        tbody.innerHTML = list.map(tx => {
            const daysOverdue = Math.floor((today - new Date(tx.dueDate)) / 86400000);
            const estFine = daysOverdue * dailyFineRate;
            return `<tr class="hover:bg-rose-50 transition">
                <td class="px-4 py-2 font-medium text-slate-900">${escapeHtml(tx.member.name)}</td>
                <td class="px-4 py-2">${escapeHtml(tx.book.title)}</td>
                <td class="px-4 py-2 text-xs text-slate-500">${tx.issueDate}</td>
                <td class="px-4 py-2 text-xs font-semibold text-rose-600">${tx.dueDate}</td>
                <td class="px-4 py-2 text-xs font-bold text-rose-700">${daysOverdue} day(s)</td>
                <td class="px-4 py-2 text-xs font-bold text-rose-600">₹${estFine.toFixed(2)}</td>
                <td class="px-4 py-2"><button onclick="quickReturn(${tx.id})" class="text-xs font-semibold text-blue-600 hover:text-blue-800 underline">Return</button></td>
            </tr>`;
        }).join('');
    } catch {
        document.getElementById('overdueDetailTable').innerHTML = '<tr><td colspan="7" class="text-center py-4 text-rose-400">Failed to load overdue data.</td></tr>';
    }
}

// ============================================================================
// POPULAR BOOKS PANEL (Step 32)
// ============================================================================
async function loadPopularBooks() {
    try {
        const res = await fetch(`${API_BASE}/books/popular`);
        if (!res.ok) return;
        const books = await res.json();
        const tbody = document.getElementById('popularBooksTable');
        if (!tbody) return;
        if (!books.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center py-4 text-slate-400">No data yet.</td></tr>';
            return;
        }
        tbody.innerHTML = books.map((b, i) => `
            <tr class="hover:bg-slate-50 transition">
                <td class="px-4 py-2 text-xs font-bold text-slate-400">#${i + 1}</td>
                <td class="px-4 py-2 font-medium text-slate-900">${escapeHtml(b.title)}</td>
                <td class="px-4 py-2 text-xs text-slate-600">${escapeHtml(b.author)}</td>
                <td class="px-4 py-2 text-xs font-semibold text-blue-600">${b.issueCount}</td>
            </tr>
        `).join('');
    } catch (err) {
        console.error('Failed to load popular books', err);
    }
}

// ============================================================================
// OUTSTANDING FINES SUMMARY (Step 33)
// ============================================================================
function updateOutstandingFines() {
    const total = allTransactionsCache
        .filter(t => !t.finePaid && t.fineAmount > 0)
        .reduce((sum, t) => sum + (t.fineAmount || 0), 0);
    const el = document.getElementById('stat-outstanding-fines');
    if (el) el.innerText = '₹' + total.toFixed(2);
}

// ============================================================================
// BOOK MANAGEMENT
// ============================================================================
async function fetchAllBooks() {
    showSpinner();
    try {
        const res = await fetch(`${API_BASE}/books`);
        if (!res.ok) throw new Error('Failed to fetch books');
        allBooksCache = await res.json();
        renderBooksTable(allBooksCache);
    } catch (err) {
        console.error('Error loading books:', err);
        showToast('Failed to load books catalog', true);
    } finally {
        hideSpinner();
    }
}

function renderBooksTable(books, page = 1) {
    const tbody = document.getElementById('books-table-body');
    if (!tbody) return;

    if (!books || books.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="px-6 py-12 text-center"><div class="flex flex-col items-center gap-3 text-slate-400"><i class="fa-solid fa-book-open text-4xl"></i><p class="text-sm font-medium">No books found</p><p class="text-xs">Try adjusting your search or filters.</p></div></td></tr>`;
        renderPageControls('books-pagination', 0, 1, () => {});
        return;
    }

    const visibleBooks = books.slice((page - 1) * TABLE_PAGE_SIZE, page * TABLE_PAGE_SIZE);
    tbody.innerHTML = visibleBooks.map(book => {
        const isAvailable = Boolean(book.available) && (book.availableCopies > 0);
        const coverUrl = safeHttpUrl(book.coverImageUrl);
        return `
            <tr class="hover:bg-slate-50 transition">
                <td class="px-6 py-3.5 text-xs text-slate-400 font-mono">#${book.id}</td>
                <td class="px-6 py-3.5">
                    ${coverUrl ? `<img src="${escapeHtml(coverUrl)}" alt="" loading="lazy" class="w-8 h-10 object-cover rounded mb-1">` : ''}
                    <div class="font-bold text-slate-900">${escapeHtml(book.title)}</div>
                    <div class="text-xs text-slate-500">By ${escapeHtml(book.author)} (${book.publicationYear || 'N/A'})</div>
                    <div class="text-xs text-slate-400">${escapeHtml(book.catalogCode || '')}</div>
                </td>
                <td class="px-6 py-3.5">
                    <span class="text-xs bg-slate-100 text-slate-700 px-2 py-0.5 rounded-md font-medium">
                        ${escapeHtml(book.category || 'General')}
                    </span>
                </td>
                <td class="px-6 py-3.5 font-mono text-xs text-slate-600">${escapeHtml(book.isbn)}</td>
                <td class="px-6 py-3.5 text-xs font-semibold">
                    <span class="${isAvailable ? 'text-emerald-700' : 'text-slate-400'}">${book.availableCopies}</span>
                    <span class="text-slate-400">/ ${book.totalCopies}</span>
                </td>
                <td class="px-6 py-3.5">
                    <button onclick="checkBookAvailability(${book.id})" title="Click to verify availability API" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold cursor-pointer ${isAvailable ? 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200' : 'bg-rose-100 text-rose-800 hover:bg-rose-200'}">
                        <span class="w-1.5 h-1.5 rounded-full mr-1.5 ${isAvailable ? 'bg-emerald-500' : 'bg-rose-500'}"></span>
                        ${isAvailable ? 'Available' : 'Issued Out'}
                    </button>
                </td>
                <td class="px-6 py-3.5 text-right space-x-2">
                    <button onclick="openEditBookById(${book.id})" class="text-xs font-medium text-slate-600 hover:text-blue-600">
                        <i class="fa-solid fa-pen-to-square"></i> Edit
                    </button>
                    <button onclick="showBookCode(${book.id}, 'qr')" class="text-xs font-medium text-indigo-600 hover:text-indigo-800" title="Show QR code">
                        <i class="fa-solid fa-qrcode"></i>
                    </button>
                    <button onclick="showBookCode(${book.id}, 'barcode')" class="text-xs font-medium text-indigo-600 hover:text-indigo-800" title="Show barcode">
                        <i class="fa-solid fa-barcode"></i>
                    </button>
                    ${!isAvailable ? `<button onclick="reserveBook(${book.id})" class="text-xs font-medium text-amber-600 hover:text-amber-800" title="Add member to waitlist">Reserve</button>` : ''}
                    <button onclick="deleteBook(${book.id})" class="text-xs font-medium text-slate-400 hover:text-rose-600">
                        <i class="fa-solid fa-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
    renderPageControls('books-pagination', books.length, page, next => renderBooksTable(books, next));
}

function renderPageControls(containerId, totalItems, currentPage, renderPage) {
    const container = document.getElementById(containerId);
    if (!container) return;
    const pageCount = Math.max(1, Math.ceil(totalItems / TABLE_PAGE_SIZE));
    currentPage = Math.min(currentPage, pageCount);
    const start = totalItems ? (currentPage - 1) * TABLE_PAGE_SIZE + 1 : 0;
    const end = Math.min(currentPage * TABLE_PAGE_SIZE, totalItems);
    container.innerHTML = `<span class="text-xs text-slate-500 mr-3">Showing ${start}-${end} of ${totalItems}</span>
        <button data-page="${Math.max(1, currentPage - 1)}" ${currentPage === 1 ? 'disabled' : ''} class="px-3 py-1 text-xs rounded border disabled:opacity-40">Previous</button>
        <span class="text-xs px-3 text-slate-600">Page ${currentPage} / ${pageCount}</span>
        <button data-page="${Math.min(pageCount, currentPage + 1)}" ${currentPage === pageCount ? 'disabled' : ''} class="px-3 py-1 text-xs rounded border disabled:opacity-40">Next</button>`;
    container.querySelectorAll('button[data-page]').forEach(button => {
        button.addEventListener('click', () => renderPage(Number(button.dataset.page)));
    });
}

function safeHttpUrl(value) {
    if (!value) return '';
    try {
        const url = new URL(value, window.location.origin);
        return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : '';
    } catch {
        return '';
    }
}

let bookSearchTimer;
function handleBookSearch() {
    clearTimeout(bookSearchTimer);
    bookSearchTimer = setTimeout(async () => {
        const query = document.getElementById('bookSearchInput').value.trim();
        try {
            const res = await fetch(`${API_BASE}/books/search?query=${encodeURIComponent(query)}`);
            if (res.ok) {
                const results = await res.json();
                renderBooksTable(results);
            }
        } catch (err) {
            console.error('Search error:', err);
        }
    }, 250);
}

// Step 20: Load categories from API
async function loadCategories() {
    try {
        const res = await fetch(`${API_BASE}/books/categories`);
        if (res.ok) {
            const cats = await res.json();
            const sel = document.getElementById('bookCategoryFilter');
            if (!sel) return;
            sel.innerHTML = '<option value="">All Categories</option>' +
                cats.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
        }
    } catch (err) {
        console.error('Failed to load categories', err);
    }
}

// Step 21: Filter books by category via server call
async function filterBooksByCategory() {
    const cat = document.getElementById('bookCategoryFilter').value;
    if (!cat) {
        renderBooksTable(allBooksCache);
        return;
    }
    try {
        const res = await fetch(`${API_BASE}/books/search?query=${encodeURIComponent(cat)}`);
        if (res.ok) {
            const results = await res.json();
            renderBooksTable(results);
        }
    } catch (err) {
        console.error(err);
    }
}

async function checkBookAvailability(id) {
    try {
        const res = await fetch(`${API_BASE}/books/${id}/availability`);
        if (res.ok) {
            const data = await res.json();
            showToast(`"${data.title}": ${data.available ? 'Available (' + data.availableCopies + ' copies)' : 'Unavailable (0 copies)'}`);
        }
    } catch (err) {
        showToast('Error checking availability', true);
    }
}

async function handleAddBookSubmit(e) {
    e.preventDefault();
    clearFieldErrors('addBookForm');
    const title = document.getElementById('newBookTitle').value.trim();
    const author = document.getElementById('newBookAuthor').value.trim();
    const isbn = document.getElementById('newBookIsbn').value.trim();
    const category = document.getElementById('newBookCategory').value.trim();
    const copies = parseInt(document.getElementById('newBookCopies').value, 10) || 1;
    const year = parseInt(document.getElementById('newBookYear').value, 10) || 2024;
    const coverImageUrl = document.getElementById('newBookCoverUrl').value.trim();

    const payload = {
        title, author, isbn, category,
        totalCopies: copies,
        availableCopies: copies,
        publicationYear: year,
        available: true,
        coverImageUrl
    };

    try {
        const res = await fetch(`${API_BASE}/books`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json();
            const fieldErrors = errData.fieldErrors;
            const mainMsg = errData.message || 'Failed to add book';
            const errObj = new Error(mainMsg);
            errObj.fieldErrors = fieldErrors;
            throw errObj;
        }

        closeModal('addBookModal');
        e.target.reset();
        showToast('Book added successfully!');
        fetchAllBooks();
        fetchDashboardStats();
    } catch (err) {
        if (err.fieldErrors) {
            Object.entries(err.fieldErrors).forEach(([field, msg]) =>
                showFieldError('newBook' + field.charAt(0).toUpperCase() + field.slice(1), msg)
            );
        }
        showToast(err.message, true);
    }
}

function openEditBookModal(book) {
    document.getElementById('editBookId').value = book.id;
    document.getElementById('editBookTitle').value = book.title;
    document.getElementById('editBookAuthor').value = book.author;
    document.getElementById('editBookIsbn').value = book.isbn;
    document.getElementById('editBookCategory').value = book.category || '';
    document.getElementById('editBookCopies').value = book.totalCopies || 1;
    document.getElementById('editBookCoverUrl').value = book.coverImageUrl || '';
    openModal('editBookModal');
}

function openEditBookById(id) {
    const book = allBooksCache.find(item => item.id === id);
    if (book) openEditBookModal(book);
}

async function handleEditBookSubmit(e) {
    e.preventDefault();
    clearFieldErrors('editBookForm');
    const id = parseInt(document.getElementById('editBookId').value, 10);
    const title = document.getElementById('editBookTitle').value.trim();
    const author = document.getElementById('editBookAuthor').value.trim();
    const isbn = document.getElementById('editBookIsbn').value.trim();
    const category = document.getElementById('editBookCategory').value.trim();
    const copies = parseInt(document.getElementById('editBookCopies').value, 10);
    const coverImageUrl = document.getElementById('editBookCoverUrl').value.trim();

    const payload = { title, author, isbn, category, totalCopies: copies, coverImageUrl };

    try {
        const res = await fetch(`${API_BASE}/books/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json();
            const fieldErrors = errData.fieldErrors;
            const mainMsg = errData.message || 'Failed to update book';
            const errObj = new Error(mainMsg);
            errObj.fieldErrors = fieldErrors;
            throw errObj;
        }

        closeModal('editBookModal');
        showToast('Book updated successfully!');
        fetchAllBooks();
        fetchDashboardStats();
    } catch (err) {
        if (err.fieldErrors) {
            Object.entries(err.fieldErrors).forEach(([field, msg]) =>
                showFieldError('editBook' + field.charAt(0).toUpperCase() + field.slice(1), msg)
            );
        }
        showToast(err.message, true);
    }
}

async function deleteBook(id) {
    showConfirm('Delete Book', 'Are you sure you want to delete this book? This cannot be undone.', async () => {
        try {
            const res = await fetch(`${API_BASE}/books/${id}`, { method: 'DELETE' });
            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.message || 'Failed to delete book');
            }

            async function showBookCode(id, type) {
                try {
                    const response = await fetch(`${API_BASE}/books/${id}/${type === 'barcode' ? 'barcode' : 'qr'}.png`);
                    if (!response.ok) throw new Error('Could not generate this book code.');
                    const book = allBooksCache.find(item => item.id === id);
                    const image = document.getElementById('bookCodeImage');
                    if (image.dataset.objectUrl) URL.revokeObjectURL(image.dataset.objectUrl);
                    const objectUrl = URL.createObjectURL(await response.blob());
                    image.src = objectUrl;
                    image.dataset.objectUrl = objectUrl;
                    document.getElementById('bookCodeTitle').innerText = type === 'barcode' ? 'Book barcode' : 'Book QR code';
                    document.getElementById('bookCodeValue').innerText = book?.catalogCode || book?.isbn || '';
                    openModal('bookCodeModal');
                } catch (error) {
                    showToast(error.message, true);
                }
            }

            async function reserveBook(bookId) {
                const membershipNumber = window.prompt('Enter the member ID to add to the waitlist:');
                if (!membershipNumber) return;
                const member = allMembersCache.find(item =>
                    item.membershipNumber.toLowerCase() === membershipNumber.trim().toLowerCase());
                if (!member) {
                    showToast('No member found with that membership number.', true);
                    return;
                }
                try {
                    const response = await fetch(`${API_BASE}/reservations`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ bookId, memberId: member.id })
                    });
                    const result = await response.json();
                    if (!response.ok) throw new Error(result.message || 'Could not reserve this book.');
                    showToast(`Added ${member.name} to the reservation waitlist.`);
                    loadReservations();
                } catch (error) {
                    showToast(error.message, true);
                }
            }

            async function loadReservations() {
                const tbody = document.getElementById('reservations-table-body');
                if (!tbody) return;
                try {
                    const response = await fetch(`${API_BASE}/reservations`);
                    if (!response.ok) throw new Error('Failed to load reservations.');
                    const reservations = await response.json();
                    tbody.innerHTML = reservations.length ? reservations.map(reservation => `
                        <tr>
                            <td class="px-5 py-3">${escapeHtml(reservation.book.title)}</td>
                            <td class="px-5 py-3">${escapeHtml(reservation.member.name)} (${escapeHtml(reservation.member.membershipNumber)})</td>
                            <td class="px-5 py-3">${escapeHtml(reservation.status)}</td>
                            <td class="px-5 py-3">${reservation.readyUntil || '--'}</td>
                            <td class="px-5 py-3"><button onclick="cancelReservation(${reservation.id})" class="text-xs text-rose-600">Cancel</button></td>
                        </tr>`).join('') : '<tr><td colspan="5" class="px-5 py-6 text-center text-slate-400">No active reservations.</td></tr>';
                } catch (error) {
                    tbody.innerHTML = `<tr><td colspan="5" class="px-5 py-6 text-center text-rose-500">${escapeHtml(error.message)}</td></tr>`;
                }
            }

            async function cancelReservation(id) {
                try {
                    const response = await fetch(`${API_BASE}/reservations/${id}`, { method: 'DELETE' });
                    const result = await response.json();
                    if (!response.ok) throw new Error(result.message || 'Could not cancel reservation.');
                    showToast(result.message);
                    loadReservations();
                } catch (error) {
                    showToast(error.message, true);
                }
            }

            async function importBooksCSV(input) {
                const file = input.files?.[0];
                if (!file) return;
                try {
                    const form = new FormData();
                    form.append('file', file);
                    const response = await fetch(`${API_BASE}/books/import`, { method: 'POST', body: form });
                    const result = await response.json();
                    if (!response.ok) throw new Error(result.message || 'Import failed.');
                    showToast(`Imported ${result.imported} book(s); ${result.failed} row(s) need attention.`);
                    if (result.errors?.length) console.warn('Book CSV import row errors:', result.errors);
                    fetchAllBooks();
                    fetchDashboardStats();
                } catch (error) {
                    showToast(error.message, true);
                } finally {
                    input.value = '';
                }
            }

            async function downloadReport(path) {
                try {
                    const response = await fetch(path);
                    if (!response.ok) {
                        const error = await response.json();
                        throw new Error(error.message || 'Report download failed.');
                    }
                    const url = URL.createObjectURL(await response.blob());
                    const anchor = document.createElement('a');
                    anchor.href = url;
                    anchor.download = path.split('/').pop();
                    anchor.click();
                    setTimeout(() => URL.revokeObjectURL(url), 1000);
                } catch (error) {
                    showToast(error.message, true);
                }
            }
            showToast('Book removed from library catalog');
            fetchAllBooks();
            fetchDashboardStats();
        } catch (err) {
            showToast(err.message, true);
        }
    });
}

// ============================================================================
// MEMBER MANAGEMENT
// ============================================================================
async function fetchAllMembers() {
    showSpinner();
    try {
        const res = await fetch(`${API_BASE}/members`);
        if (!res.ok) throw new Error('Failed to fetch members');
        allMembersCache = await res.json();
        renderMembersTable(allMembersCache);
    } catch (err) {
        console.error('Error loading members:', err);
    } finally {
        hideSpinner();
    }
}

function renderMembersTable(members, page = 1) {
    const tbody = document.getElementById('members-table-body');
    if (!tbody) return;

    if (!members || members.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="px-6 py-12 text-center"><div class="flex flex-col items-center gap-3 text-slate-400"><i class="fa-solid fa-users text-4xl"></i><p class="text-sm font-medium">No members found</p><p class="text-xs">Try adjusting your search or filters.</p></div></td></tr>`;
        renderPageControls('members-pagination', 0, 1, () => {});
        return;
    }

    const visibleMembers = members.slice((page - 1) * TABLE_PAGE_SIZE, page * TABLE_PAGE_SIZE);
    tbody.innerHTML = visibleMembers.map(m => `
        <tr class="hover:bg-slate-50 transition">
            <td class="px-6 py-3.5 font-mono text-xs font-semibold text-purple-700">${escapeHtml(m.membershipNumber)}</td>
            <td class="px-6 py-3.5 font-bold text-slate-900">${escapeHtml(m.name)}</td>
            <td class="px-6 py-3.5">
                <span class="text-xs font-semibold px-2 py-0.5 rounded-full ${m.membershipType === 'FACULTY' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-800'}">
                    ${escapeHtml(m.membershipType)}
                </span>
                <span class="text-xs text-slate-500 ml-1">(${escapeHtml(m.department || 'N/A')})</span>
            </td>
            <td class="px-6 py-3.5 text-xs text-slate-600">
                <div><i class="fa-regular fa-envelope text-slate-400 mr-1"></i>${escapeHtml(m.email)}</div>
                <div class="text-slate-400 mt-0.5"><i class="fa-solid fa-phone text-slate-400 mr-1"></i>${escapeHtml(m.phone || 'N/A')}</div>
            </td>
            <td class="px-6 py-3.5">
                <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${m.active ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}">
                    ${m.active ? 'Active' : 'Inactive'}
                </span>
            </td>
            <td class="px-6 py-3.5 text-right space-x-1">
                <button onclick="openEditMemberById(${m.id})" class="text-xs font-medium text-slate-600 hover:text-blue-600 mr-2">
                    <i class="fa-solid fa-pen-to-square"></i> Edit
                </button>
                <button onclick="showMemberQr(${m.id})" class="text-xs font-medium text-indigo-600 hover:text-indigo-800" title="Show student ID QR">
                    <i class="fa-solid fa-qrcode"></i> ID QR
                </button>
                <button onclick="toggleMemberStatus(${m.id}, ${m.active})" class="text-xs font-medium ${m.active ? 'text-rose-500 hover:text-rose-700' : 'text-emerald-600 hover:text-emerald-800'}">
                    <i class="fa-solid ${m.active ? 'fa-ban' : 'fa-circle-check'}"></i> ${m.active ? 'Deactivate' : 'Activate'}
                </button>
                <button onclick="openMemberHistory(${m.id})" class="text-xs font-medium text-slate-600 hover:text-purple-600 ml-2">
                    <i class="fa-solid fa-history"></i> History
                </button>
                <button onclick="deleteMember(${m.id})" class="text-xs font-medium text-slate-400 hover:text-rose-600 ml-2">
                    <i class="fa-solid fa-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
    renderPageControls('members-pagination', members.length, page, next => renderMembersTable(members, next));
}

let memberSearchTimer;
function handleMemberSearch() {
    clearTimeout(memberSearchTimer);
    memberSearchTimer = setTimeout(async () => {
        const query = document.getElementById('memberSearchInput').value.trim();
        try {
            const res = await fetch(`${API_BASE}/members/search?query=${encodeURIComponent(query)}`);
            if (res.ok) {
                const results = await res.json();
                renderMembersTable(results);
            }
        } catch (err) {
            console.error('Member search error:', err);
        }
    }, 250);
}

async function handleAddMemberSubmit(e) {
    e.preventDefault();
    clearFieldErrors('addMemberForm');
    const name = document.getElementById('newMemberName').value.trim();
    const membershipNumber = document.getElementById('newMemberNum').value.trim();
    const membershipType = document.getElementById('newMemberType').value;
    const email = document.getElementById('newMemberEmail').value.trim();
    const phone = document.getElementById('newMemberPhone').value.trim();
    const department = document.getElementById('newMemberDept').value.trim();

    const payload = { name, membershipNumber, membershipType, email, phone, department, active: true };

    try {
        const res = await fetch(`${API_BASE}/members`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json();
            const fieldErrors = errData.fieldErrors;
            const mainMsg = errData.message || 'Failed to register member';
            const errObj = new Error(mainMsg);
            errObj.fieldErrors = fieldErrors;
            throw errObj;
        }

        closeModal('addMemberModal');
        e.target.reset();
        showToast('Member registered successfully!');
        fetchAllMembers();
        fetchDashboardStats();
    } catch (err) {
        if (err.fieldErrors) {
            Object.entries(err.fieldErrors).forEach(([field, msg]) =>
                showFieldError('newMember' + field.charAt(0).toUpperCase() + field.slice(1), msg)
            );
        }
        showToast(err.message, true);
    }
}

async function deleteMember(id) {
    showConfirm('Delete Member', 'Are you sure you want to delete this member? This cannot be undone.', async () => {
        try {
            const res = await fetch(`${API_BASE}/members/${id}`, { method: 'DELETE' });
            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.message || 'Failed to delete member');
            }
            showToast('Member deleted');
            fetchAllMembers();
            fetchDashboardStats();
        } catch (err) {
            showToast(err.message, true);
        }
    });
}

// Step 23: Edit Member
function openEditMemberModal(member) {
    document.getElementById('editMemberId').value = member.id;
    document.getElementById('editMemberName').value = member.name;
    document.getElementById('editMemberNum').value = member.membershipNumber;
    document.getElementById('editMemberType').value = member.membershipType;
    document.getElementById('editMemberEmail').value = member.email;
    document.getElementById('editMemberPhone').value = member.phone || '';
    document.getElementById('editMemberDept').value = member.department || '';
    openModal('editMemberModal');
}

function openEditMemberById(id) {
    const member = allMembersCache.find(item => item.id === id);
    if (member) openEditMemberModal(member);
}

async function handleEditMemberSubmit(e) {
    e.preventDefault();
    clearFieldErrors('editMemberForm');
    const id = parseInt(document.getElementById('editMemberId').value, 10);
    const payload = {
        name: document.getElementById('editMemberName').value.trim(),
        membershipNumber: document.getElementById('editMemberNum').value.trim(),
        membershipType: document.getElementById('editMemberType').value,
        email: document.getElementById('editMemberEmail').value.trim(),
        phone: document.getElementById('editMemberPhone').value.trim(),
        department: document.getElementById('editMemberDept').value.trim()
    };
    try {
        const res = await fetch(`${API_BASE}/members/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const errData = await res.json();
            if (errData.fieldErrors) {
                Object.entries(errData.fieldErrors).forEach(([f, m]) =>
                    showFieldError('editMember' + f.charAt(0).toUpperCase() + f.slice(1), m)
                );
            }
            throw new Error(errData.message || 'Failed to update member');
        }
        closeModal('editMemberModal');
        showToast('Member updated successfully!');
        fetchAllMembers();
        fetchDashboardStats();
    } catch (err) {
        showToast(err.message, true);
    }
}

// Step 24: Member status toggle
async function toggleMemberStatus(id, currentActive) {
    try {
        const res = await fetch(`${API_BASE}/members/${id}/status`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ active: !currentActive })
        });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to toggle status');
        }
        showToast(!currentActive ? 'Member activated' : 'Member deactivated');
        fetchAllMembers();
        fetchDashboardStats();
    } catch (err) {
        showToast(err.message, true);
    }
}

// Step 25: Member history
async function openMemberHistory(memberId) {
    const memberName = allMembersCache.find(member => member.id === memberId)?.name || `Member #${memberId}`;
    document.getElementById('historyMemberName').innerText = memberName + ' — Transaction History';
    const tbody = document.getElementById('historyTransactionsTable');
    tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-slate-400">Loading...</td></tr>';
    openModal('memberHistoryModal');
    try {
        const res = await fetch(`${API_BASE}/transactions/member/${memberId}`);
        if (!res.ok) throw new Error('Failed to load history');
        const list = await res.json();
        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-slate-400">No transactions found.</td></tr>';
            return;
        }
        const today = new Date();
        tbody.innerHTML = list.map(tx => {
            const isReturned = tx.status === 'RETURNED';
            const isOverdue = !isReturned && new Date(tx.dueDate) < today;
            return `<tr class="hover:bg-slate-50 transition">
                <td class="px-4 py-2 font-mono text-xs text-slate-400">#${tx.id}</td>
                <td class="px-4 py-2 font-medium text-slate-900">${escapeHtml(tx.book.title)}</td>
                <td class="px-4 py-2 text-xs text-slate-500">${tx.issueDate}</td>
                <td class="px-4 py-2 text-xs font-semibold ${isOverdue ? 'text-rose-600' : 'text-slate-700'}">${tx.dueDate}</td>
                <td class="px-4 py-2 text-xs text-slate-500">${tx.returnDate || '--'}</td>
                <td class="px-4 py-2 text-xs font-semibold ${tx.fineAmount > 0 ? 'text-rose-600' : 'text-slate-400'}">₹${Number(tx.fineAmount).toFixed(2)}</td>
                <td class="px-4 py-2"><span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${isReturned ? 'bg-emerald-100 text-emerald-800' : isOverdue ? 'bg-rose-100 text-rose-800' : 'bg-blue-100 text-blue-800'}">${isReturned ? 'Returned' : isOverdue ? 'Overdue' : 'Issued'}</span></td>
            </tr>`;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-rose-400">${err.message}</td></tr>`;
    }
}

// ============================================================================
// ISSUE WORKFLOW & RETURN WORKFLOW
// ============================================================================
async function populateIssueDropdowns() {
    const memberSelect = document.getElementById('issueMemberSelect');
    if (memberSelect) {
        try {
            const res = await fetch(`${API_BASE}/members`);
            if (res.ok) {
                const members = await res.json();
                memberSelect.innerHTML = '<option value="">-- Choose Member --</option>' +
                    members.map(m => `<option value="${m.id}">${escapeHtml(m.name)} (${escapeHtml(m.membershipNumber)})</option>`).join('');
            }
        } catch (err) {
            console.error(err);
        }
    }

    const bookSelect = document.getElementById('issueBookSelect');
    if (bookSelect) {
        try {
            const res = await fetch(`${API_BASE}/books/available`);
            if (res.ok) {
                const books = await res.json();
                bookSelect.innerHTML = '<option value="">-- Choose Available Book --</option>' +
                    books.map(b => `<option value="${b.id}">${escapeHtml(b.title)} (${b.availableCopies} available)</option>`).join('');
            }
        } catch (err) {
            console.error(err);
        }
    }
}

async function populateReturnDropdown() {
    const returnSelect = document.getElementById('returnTransactionSelect');
    if (!returnSelect) return;

    try {
        const res = await fetch(`${API_BASE}/transactions/active`);
        if (res.ok) {
            const activeList = await res.json();
            returnSelect.innerHTML = '<option value="">-- Choose Issued Book to Return --</option>' +
                activeList.map(tx => `
                    <option value="${tx.id}" data-due="${tx.dueDate}">
                        #${tx.id} - ${escapeHtml(tx.book.title)} &rarr; ${escapeHtml(tx.member.name)} (Due: ${tx.dueDate})
                    </option>
                `).join('');
            previewReturnFine();
        }
    } catch (err) {
        console.error(err);
    }
}

function previewReturnFine() {
    const select = document.getElementById('returnTransactionSelect');
    const returnDateInput = document.getElementById('customReturnDate');
    const previewDueDate = document.getElementById('previewDueDate');
    const previewLateDays = document.getElementById('previewLateDays');
    const previewFine = document.getElementById('previewFineAmount');

    if (!select || !select.value) {
        if (previewDueDate) previewDueDate.innerText = '--';
        if (previewLateDays) previewLateDays.innerText = '0 days';
        if (previewFine) previewFine.innerText = '₹0.00';
        return;
    }

    const selectedOption = select.options[select.selectedIndex];
    const dueDateStr = selectedOption.getAttribute('data-due');
    if (!dueDateStr) return;

    if (previewDueDate) previewDueDate.innerText = dueDateStr;
    const dueDate = new Date(dueDateStr);
    const returnDate = returnDateInput && returnDateInput.value ? new Date(returnDateInput.value) : new Date();

    const diffTime = returnDate.getTime() - dueDate.getTime();
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays > 0) {
        const fine = diffDays * dailyFineRate;
        if (previewLateDays) {
            previewLateDays.innerText = `${diffDays} day(s) overdue`;
            previewLateDays.className = 'font-bold text-rose-600';
        }
        if (previewFine) {
            previewFine.innerText = `₹${fine.toFixed(2)}`;
            previewFine.className = 'text-rose-600 text-base font-bold';
        }
    } else {
        if (previewLateDays) {
            previewLateDays.innerText = 'On time';
            previewLateDays.className = 'font-medium text-emerald-600';
        }
        if (previewFine) {
            previewFine.innerText = '₹0.00 (No fine)';
            previewFine.className = 'text-emerald-700 text-base font-bold';
        }
    }
}

async function handleIssueBookSubmit(e) {
    e.preventDefault();
    const memberId = parseInt(document.getElementById('issueMemberSelect').value, 10);
    const bookId = parseInt(document.getElementById('issueBookSelect').value, 10);
    const loanDays = parseInt(document.getElementById('issueLoanDays').value, 10) || 14;

    if (!memberId || !bookId) {
        showToast('Please select both a member and a book', true);
        return;
    }

    showSpinner();
    try {
        const res = await fetch(`${API_BASE}/transactions/issue`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ memberId, bookId, loanDays })
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to issue book');
        }

        const tx = await res.json();
        showToast(`Book issued! Due Date: ${tx.dueDate}`);
        e.target.reset();
        document.getElementById('issueLoanDays').value = 14;

        populateIssueDropdowns();
        populateReturnDropdown();
        fetchAllTransactions();
        fetchDashboardStats();
    } catch (err) {
        showToast(err.message, true);
    } finally {
        hideSpinner();
    }
}

async function handleReturnBookSubmit(e) {
    e.preventDefault();
    const transactionId = parseInt(document.getElementById('returnTransactionSelect').value, 10);
    const returnDate = document.getElementById('customReturnDate').value;

    if (!transactionId) {
        showToast('Please select an active transaction to return', true);
        return;
    }

    showSpinner();
    try {
        const res = await fetch(`${API_BASE}/transactions/return`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transactionId, returnDate })
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to return book');
        }

        const data = await res.json();
        showToast(data.message);

        populateReturnDropdown();
        populateIssueDropdowns();
        fetchAllTransactions();
        fetchDashboardStats();
    } catch (err) {
        showToast(err.message, true);
    } finally {
        hideSpinner();
    }
}

// Step 30: quickReturn - async, await populateReturnDropdown
async function quickReturn(transactionId) {
    switchTab('transactions');
    await populateReturnDropdown();
    const select = document.getElementById('returnTransactionSelect');
    if (select) {
        select.value = transactionId;
        previewReturnFine();
        select.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

// Step 28: Pay Fine
async function payFine(txId) {
    try {
        const method = window.prompt('Payment method: CASH, CARD, UPI, or BANK_TRANSFER', 'CASH');
        if (method === null) return;
        const res = await fetch(`${API_BASE}/transactions/${txId}/pay-fine?method=${encodeURIComponent(method)}`, { method: 'POST' });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to pay fine');
        }
        showToast('Fine marked as paid!');
        fetchAllTransactions();
        fetchDashboardStats();
        updateOutstandingFines();
    } catch (err) {
        showToast(err.message, true);
    }
}

// Step 29: Renew Book
async function renewBook(txId) {
    try {
        const res = await fetch(`${API_BASE}/transactions/${txId}/renew?days=7`, { method: 'PUT' });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to renew');
        }
        const tx = await res.json();
        showToast('Loan renewed! New due date: ' + tx.dueDate);
        fetchAllTransactions();
        populateReturnDropdown();
    } catch (err) {
        showToast(err.message, true);
    }
}

// ============================================================================
// TRANSACTIONS LOG & FILTERING
// ============================================================================
async function fetchAllTransactions() {
    showSpinner();
    try {
        const res = await fetch(`${API_BASE}/transactions`);
        if (!res.ok) throw new Error('Failed to fetch transactions');
        allTransactionsCache = await res.json();
        transactionPage = 1;
        renderTransactionsTable(allTransactionsCache);
        updateOutstandingFines(); // Step 33
    } catch (err) {
        console.error('Error fetching transactions:', err);
    } finally {
        hideSpinner();
    }
}

// Step 22: Transaction search with debounce
let txSearchTimer;
function handleTxSearch() {
    clearTimeout(txSearchTimer);
    txSearchTimer = setTimeout(() => {
        const q = document.getElementById('txSearchInput').value.trim().toLowerCase();
        transactionPage = 1;
        if (!q) {
            renderTransactionsTable(allTransactionsCache);
            return;
        }
        const filtered = allTransactionsCache.filter(t =>
            (t.book.title && t.book.title.toLowerCase().includes(q)) ||
            (t.member.name && t.member.name.toLowerCase().includes(q))
        );
        renderTransactionsTable(filtered);
    }, 250);
}

// Step 27: Render transactions with Actions column
function renderTransactionsTable(list) {
    const tbody = document.getElementById('transactions-table-body');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = `<tr><td colspan="9" class="px-6 py-12 text-center"><div class="flex flex-col items-center gap-3 text-slate-400"><i class="fa-solid fa-list text-4xl"></i><p class="text-sm font-medium">No transaction records found</p><p class="text-xs">Try adjusting your search or filters.</p></div></td></tr>`;
        renderPageControls('transactions-pagination', 0, 1, () => {});
        return;
    }

    transactionPage = Math.min(transactionPage, Math.max(1, Math.ceil(list.length / TABLE_PAGE_SIZE)));
    const visibleTransactions = list.slice((transactionPage - 1) * TABLE_PAGE_SIZE, transactionPage * TABLE_PAGE_SIZE);
    const today = new Date();
    tbody.innerHTML = visibleTransactions.map(tx => {
        const isReturned = tx.status === 'RETURNED';
        const isOverdue = !isReturned && new Date(tx.dueDate) < today;

        let actionsHtml = '';
        if (!isReturned) {
            // ISSUED status: Return + Renew buttons
            actionsHtml = `
                <button onclick="quickReturn(${tx.id})" class="text-xs font-semibold text-emerald-600 hover:text-emerald-800 mr-2" title="Return book">
                    <i class="fa-solid fa-rotate-left"></i> Return
                </button>
                <button onclick="renewBook(${tx.id})" class="text-xs font-semibold text-blue-600 hover:text-blue-800" title="Renew loan by 7 days">
                    <i class="fa-solid fa-calendar-plus"></i> Renew
                </button>
            `;
        } else if (tx.fineAmount > 0 && !tx.finePaid) {
            // RETURNED with unpaid fine: Pay Fine button
            actionsHtml = `
                <button onclick="payFine(${tx.id})" class="text-xs font-semibold text-amber-600 hover:text-amber-800" title="Mark fine as paid">
                    <i class="fa-solid fa-indian-rupee-sign"></i> Pay Fine
                </button>
            `;
        } else if (tx.fineAmount > 0 && tx.finePaid) {
            // RETURNED with paid fine: Paid badge
            actionsHtml = `<span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700"><i class="fa-solid fa-check mr-1"></i>Paid</span>`;
        } else {
            actionsHtml = '<span class="text-slate-400 text-xs">--</span>';
        }

        return `
            <tr class="hover:bg-slate-50 transition">
                <td class="px-6 py-3.5 font-mono text-xs text-slate-400">#${tx.id}</td>
                <td class="px-6 py-3.5 font-bold text-slate-900">${escapeHtml(tx.book.title)}</td>
                <td class="px-6 py-3.5">${escapeHtml(tx.member.name)} <span class="text-xs text-slate-400 font-mono">(${escapeHtml(tx.member.membershipNumber)})</span></td>
                <td class="px-6 py-3.5 text-xs text-slate-500">${tx.issueDate}</td>
                <td class="px-6 py-3.5 text-xs font-semibold ${isOverdue ? 'text-rose-600' : 'text-slate-700'}">${tx.dueDate}</td>
                <td class="px-6 py-3.5 text-xs text-slate-500">${tx.returnDate || '--'}</td>
                <td class="px-6 py-3.5 font-semibold text-xs ${tx.fineAmount > 0 ? 'text-rose-600' : 'text-slate-400'}">
                    ₹${Number(tx.fineAmount).toFixed(2)}
                </td>
                <td class="px-6 py-3.5">
                    <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                        isReturned ? 'bg-emerald-100 text-emerald-800' :
                        isOverdue ? 'bg-rose-100 text-rose-800' : 'bg-blue-100 text-blue-800'
                    }">
                        ${isReturned ? 'Returned' : isOverdue ? 'Overdue' : 'Issued'}
                    </span>
                </td>
                <td class="px-6 py-3.5 text-right whitespace-nowrap">${actionsHtml}</td>
            </tr>
        `;
    }).join('');
    renderPageControls('transactions-pagination', list.length, transactionPage,
        next => { transactionPage = next; renderTransactionsTable(list); });
}

function filterTransactions(filter) {
    transactionPage = 1;
    ['all', 'active', 'overdue'].forEach(btn => {
        const el = document.getElementById(`btn-tx-${btn}`);
        if (el) {
            el.className = 'px-3 py-1 text-xs font-semibold rounded-lg bg-slate-100 text-slate-600 hover:bg-slate-200';
        }
    });

    const activeBtn = document.getElementById(`btn-tx-${filter === 'ISSUED' ? 'active' : filter}`);
    if (activeBtn) activeBtn.className = 'px-3 py-1 text-xs font-semibold rounded-lg bg-blue-100 text-blue-700';

    const today = new Date();
    if (filter === 'all') {
        renderTransactionsTable(allTransactionsCache);
    } else if (filter === 'ISSUED') {
        renderTransactionsTable(allTransactionsCache.filter(t => t.status === 'ISSUED'));
    } else if (filter === 'overdue') {
        renderTransactionsTable(allTransactionsCache.filter(t => t.status === 'ISSUED' && new Date(t.dueDate) < today));
    }
}

// ============================================================================
// CSV EXPORT (Step 34)
// ============================================================================
function exportTableToCSV(data, filename, columns) {
    const escapeCsv = value => `"${String(value ?? '').replace(/"/g, '""')}"`;
    const header = columns.map(c => escapeCsv(c.header)).join(',');
    const rows = data.map(row =>
        columns.map(c => {
            const val = typeof c.key === 'function' ? c.key(row) : (row[c.key] ?? '');
            const text = String(val);
            const safeText = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text;
            return escapeCsv(safeText);
        }).join(',')
    );
    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
}

function exportBooksCSV() {
    exportTableToCSV(allBooksCache, 'books.csv', [
        { header: 'ID', key: 'id' },
        { header: 'Title', key: 'title' },
        { header: 'Author', key: 'author' },
        { header: 'ISBN', key: 'isbn' },
        { header: 'Category', key: 'category' },
        { header: 'Total Copies', key: 'totalCopies' },
        { header: 'Available', key: 'availableCopies' },
        { header: 'Issue Count', key: 'issueCount' }
    ]);
}

function exportMembersCSV() {
    exportTableToCSV(allMembersCache, 'members.csv', [
        { header: 'ID', key: 'id' },
        { header: 'Name', key: 'name' },
        { header: 'Membership Number', key: 'membershipNumber' },
        { header: 'Type', key: 'membershipType' },
        { header: 'Department', key: 'department' },
        { header: 'Email', key: 'email' },
        { header: 'Phone', key: 'phone' },
        { header: 'Status', key: m => m.active ? 'Active' : 'Inactive' }
    ]);
}

function exportTransactionsCSV() {
    exportTableToCSV(allTransactionsCache, 'transactions.csv', [
        { header: 'Transaction ID', key: 'id' },
        { header: 'Book', key: t => t.book.title },
        { header: 'Member', key: t => t.member.name },
        { header: 'Issue Date', key: 'issueDate' },
        { header: 'Due Date', key: 'dueDate' },
        { header: 'Return Date', key: 'returnDate' },
        { header: 'Fine', key: 'fineAmount' },
        { header: 'Fine Paid', key: t => t.finePaid ? 'Yes' : 'No' },
        { header: 'Payment Method', key: 'finePaymentMethod' }
    ]);
}

// ============================================================================
// UI HELPERS (Modals, Toast, Sanitization)
// ============================================================================
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('hidden');
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.add('hidden');
}

let toastTimer;
function showToast(message, isError = false) {
    const toast = document.getElementById('toast');
    const toastMsg = document.getElementById('toastMessage');
    const toastIcon = document.getElementById('toastIcon');
    if (!toast) return;

    toastMsg.innerText = message;
    if (isError) {
        toastIcon.className = 'fa-solid fa-circle-exclamation text-rose-400';
    } else {
        toastIcon.className = 'fa-solid fa-circle-check text-emerald-400';
    }

    toast.classList.remove('translate-y-20', 'opacity-0');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.classList.add('translate-y-20', 'opacity-0');
    }, 3500);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
