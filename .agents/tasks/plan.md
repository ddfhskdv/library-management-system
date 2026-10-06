# Implementation Plan — Library Management System Completion

**Project:** Spring Boot 3.2.5 + H2 + Tailwind CSS  
**Build command:** `mvn compile -f "c:\Users\hp\OneDrive\Desktop\library management\pom.xml"`  
**Test command:** `mvn test -f "c:\Users\hp\OneDrive\Desktop\library management\pom.xml"`  
**Port:** 8081 (application.properties)

---

## GROUP 1 — Critical Bug Fixes (must be done first for clean compile)

- [ ] 1. **Add Springdoc OpenAPI dependency** — pom.xml must be updated before any mvn compile step.  
      Add inside `<dependencies>`: `<dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.3.0</version></dependency>`  
      Files: `pom.xml`  
      Verify: `mvn compile` — BUILD SUCCESS

- [ ] 2. **Fix port mismatch (8080 → 8081)** — README and footer currently reference port 8080; app runs on 8081.  
      In `index.html` footer, change `http://localhost:8080/api/*` to `http://localhost:8081/api/*`.  
      In `application.properties`, fix the H2 console comment from `localhost:8080/h2-console` to `localhost:8081/h2-console`.  
      Files: `src/main/resources/static/index.html`, `src/main/resources/application.properties`  
      Verify: Visual inspection of footer text after `mvn compile`

- [ ] 3. **Fix GlobalExceptionHandler missing 400/409 handlers** — `IllegalArgumentException` and `IllegalStateException` currently fall through to the generic 500 handler, making duplicate-ISBN and max-books errors return HTTP 500 instead of 400/409.  
      Add two `@ExceptionHandler` methods BEFORE the generic `Exception` handler:  
      - `handleIllegalArgument(IllegalArgumentException ex)` → `HttpStatus.BAD_REQUEST` (400), `error="Bad Request"`  
      - `handleIllegalState(IllegalStateException ex)` → `HttpStatus.CONFLICT` (409), `error="Conflict"`  
      Use the same `Map<String,Object>` pattern (timestamp, status int, error string, message) as existing handlers.  
      Files: `src/main/java/com/college/library/exception/GlobalExceptionHandler.java`  
      Verify: `mvn compile` — BUILD SUCCESS

- [ ] 4. **Fix fine preview Math.ceil → Math.floor** — Frontend preview shows 1 day more than the backend calculates (ChronoUnit.DAYS.between truncates; Math.ceil rounds up).  
      In `app.js` `previewReturnFine()`, change `Math.ceil(diffTime / (1000 * 60 * 60 * 24))` to `Math.floor(...)`.  
      Files: `src/main/resources/static/js/app.js`  
      Verify: No compile step needed — visual test in browser

- [ ] 5. **Add ISBN validation and uniqueness** — `Book.isbn` has no `@NotBlank` and no unique constraint, allowing blank and duplicate ISBNs.  
      In `Book.java`, add `@NotBlank(message="ISBN is required")` and `@Size(min=10, max=13, message="ISBN must be between 10 and 13 characters")` to the isbn field; update `@Column` to include `unique=true`.  
      In `BookServiceImpl.addBook()`, after defaults block, add: check `existsByIsbn` and throw `IllegalArgumentException` if duplicate.  
      In `BookServiceImpl.updateBook()`, if ISBN is being changed, check `existsByIsbn` and throw `IllegalArgumentException` if collides with another book.  
      New import needed: `jakarta.validation.constraints.Size`.  
      Files: `src/main/java/com/college/library/entity/Book.java`, `src/main/java/com/college/library/service/impl/BookServiceImpl.java`  
      Verify: `mvn compile` — BUILD SUCCESS; `mvn test` — all 3 tests pass

---

## GROUP 2 — Backend Feature Additions (items 6–14 depend on item 5 for clean compile)

- [ ] 6. **finePaid field on Transaction** — No way to record whether an assessed fine has been collected.  
      Add `private boolean finePaid = false;` to `Transaction.java` with getter `isFinePaid()` and setter `setFinePaid(boolean)`.  
      Files: `src/main/java/com/college/library/entity/Transaction.java`  
      Verify: `mvn compile` — BUILD SUCCESS

- [ ] 7. **issueCount field on Book** — No book demand tracking exists.  
      Add `private int issueCount = 0;` to `Book.java` with getter `getIssueCount()` and setter `setIssueCount(int)`.  
      Files: `src/main/java/com/college/library/entity/Book.java`  
      Verify: `mvn compile` — BUILD SUCCESS

- [ ] 8. **MemberStatusRequest DTO** — Required by the status toggle endpoint.  
      Create `src/main/java/com/college/library/dto/MemberStatusRequest.java` with package declaration, field `private Boolean active`, getter, and setter.  
      Files: `src/main/java/com/college/library/dto/MemberStatusRequest.java` (new)  
      Verify: `mvn compile` — BUILD SUCCESS

- [ ] 9. **New repository query methods** — Backend methods needed by new service logic.  
      In `TransactionRepository`: add `List<Transaction> findByMemberIdOrderByCreatedAtDesc(Long memberId)` and `long countByMemberIdAndStatus(Long memberId, String status)` (both Spring Data derived — no @Query needed).  
      In `BookRepository`: add `@Query("SELECT DISTINCT b.category FROM Book b WHERE b.category IS NOT NULL AND b.category <> '' ORDER BY b.category") List<String> findDistinctCategories()` and `List<Book> findTop5ByOrderByIssueCountDesc()` (derived).  
      Files: `src/main/java/com/college/library/repository/TransactionRepository.java`, `src/main/java/com/college/library/repository/BookRepository.java`  
      Verify: `mvn compile` — BUILD SUCCESS

- [ ] 10. **Declare new service interface methods** — Must be declared before implementing (project convention).  
      In `TransactionService`: add `Transaction payFine(Long transactionId)`, `List<Transaction> getTransactionsByMember(Long memberId)`, `Transaction renewTransaction(Long transactionId, int days)`.  
      In `BookService`: add `List<String> getCategories()`, `List<Book> getPopularBooks()`.  
      In `MemberService`: add `Member toggleMemberStatus(Long id, Boolean active)`.  
      Files: `src/main/java/com/college/library/service/TransactionService.java`, `BookService.java`, `MemberService.java`  
      Verify: `mvn compile` — will FAIL until implementations are added in item 11 below

- [ ] 11. **Implement new service methods** — Depends on item 10 (interfaces declared).  
      `TransactionServiceImpl`:  
        - `issueBook()`: add `countByMemberIdAndStatus` guard before book fetch; throw `IllegalStateException` if `activeCount >= member.getMaxAllowedBooks()`. Also add `book.setIssueCount(book.getIssueCount() + 1)` before `bookRepository.save(book)`.  
        - `payFine(id)`: fetch tx, throw if not RETURNED or already paid, set `finePaid=true`, save and return.  
        - `getTransactionsByMember(memberId)`: return `transactionRepository.findByMemberIdOrderByCreatedAtDesc(memberId)`.  
        - `renewTransaction(id, days)`: fetch tx, throw if not ISSUED, extend `dueDate` by `days`, save and return.  
      `BookServiceImpl`:  
        - `getCategories()`: return `bookRepository.findDistinctCategories()`.  
        - `getPopularBooks()`: return `bookRepository.findTop5ByOrderByIssueCountDesc()`.  
      `MemberServiceImpl`:  
        - `toggleMemberStatus(id, active)`: fetch member, `setActive(active)`, save and return.  
      Files: `service/impl/TransactionServiceImpl.java`, `service/impl/BookServiceImpl.java`, `service/impl/MemberServiceImpl.java`  
      Verify: `mvn compile` — BUILD SUCCESS; `mvn test` — all 3 tests pass

- [ ] 12. **Add new REST endpoints to controllers** — Depends on item 11 (service methods must exist).  
      `TransactionController`:  
        - `POST /api/transactions/{id}/pay-fine` → `transactionService.payFine(id)`  
        - `GET /api/transactions/member/{memberId}` → `transactionService.getTransactionsByMember(memberId)`  
        - `PUT /api/transactions/{id}/renew` with `@RequestParam(defaultValue="7") int days` → `transactionService.renewTransaction(id, days)`  
      `BookController`:  
        - `GET /api/books/categories` → `bookService.getCategories()` returning `ResponseEntity<List<String>>`  
        - `GET /api/books/popular` → `bookService.getPopularBooks()`  
      `MemberController`:  
        - `PUT /api/members/{id}/status` accepting `@RequestBody MemberStatusRequest` → `memberService.toggleMemberStatus(id, req.getActive())`; import `com.college.library.dto.MemberStatusRequest`  
      Files: `controller/TransactionController.java`, `controller/BookController.java`, `controller/MemberController.java`  
      Verify: `mvn compile` — BUILD SUCCESS; `mvn test` — all 3 tests pass

---

## GROUP 3 — Frontend: Complete Missing UI (items 13–27 depend on GROUP 2 endpoints being available at runtime)

- [ ] 13. **Tailwind dark mode config + dark mode toggle** — Enable `class` strategy in Tailwind config so `dark:` variants work.  
      In `index.html` tailwind.config block, add `darkMode: 'class'`. Add `dark:bg-slate-900 dark:text-slate-100` to `<body>`. Add dark mode toggle button before H2 console link in header: button id=`darkModeToggle`, icon id=`darkModeIcon` (fa-moon/fa-sun), `onclick="toggleDarkMode()"`.  
      In `app.js`: add `toggleDarkMode()` (toggle `dark` class on `document.documentElement`, save to `localStorage`), `updateDarkModeIcon()` helper. In DOMContentLoaded, restore dark mode from `localStorage` and call `updateDarkModeIcon()`.  
      In `css/style.css`: add `.dark body`, `.dark .bg-white`, `.dark .bg-slate-50`, `.dark .border-slate-200`, `.dark input`, `.dark select` overrides.  
      Files: `index.html`, `js/app.js`, `css/style.css`  
      Verify: `mvn compile` — BUILD SUCCESS; browser: toggle icon switches page between light and dark; refreshing page restores preference

- [ ] 14. **Fix nav bar Issue Book button** — The button calls `openModal('issueBookModal')` but that modal does not exist, causing a silent no-op.  
      Change onclick to `switchTab('transactions')`.  
      Files: `src/main/resources/static/index.html`  
      Verify: Clicking "Issue Book" in nav bar navigates to Issue & Return tab

- [ ] 15. **Loading spinner overlay** — No visual feedback during async operations.  
      Add spinner overlay div `id="loadingOverlay"` (fixed, z-[60], hidden by default) with a Tailwind `animate-spin` circle inside.  
      Add `showSpinner()` / `hideSpinner()` helpers in `app.js`. Wrap `fetchAllBooks`, `fetchAllMembers`, `fetchAllTransactions`, `handleIssueBookSubmit`, `handleReturnBookSubmit` with spinner show/hide in `finally`.  
      Files: `index.html`, `js/app.js`  
      Verify: Initiating a book issue shows spinner briefly before resolving

- [ ] 16. **Custom confirm dialog** — Delete operations use browser `confirm()` which cannot be styled.  
      Add `id="confirmModal"` modal in HTML with title, message, Cancel, and Confirm buttons.  
      Add `showConfirm(title, message, onConfirm)` and `confirmAction()` in `app.js`. Replace `confirm()` calls in `deleteBook()` and `deleteMember()` with `showConfirm(...)`.  
      Files: `index.html`, `js/app.js`  
      Verify: Clicking Delete on a book/member shows the custom modal instead of browser dialog

- [ ] 17. **Empty state illustrations** — Tables with no results show plain text.  
      In `renderBooksTable()`, `renderMembersTable()`, `renderTransactionsTable()`: replace the plain empty `<td>` with a centered div containing a Font Awesome icon and "No results found" message.  
      Files: `js/app.js`  
      Verify: Empty search returns styled empty state in each table

- [ ] 18. **Inline form field validation** — API validation errors (`fieldErrors`) are shown as toast only, not next to the offending field.  
      Add `showFieldError(inputId, message)` and `clearFieldErrors(formId)` helpers. In catch blocks of `handleAddBookSubmit`, `handleEditBookSubmit`, `handleAddMemberSubmit`, `handleEditMemberSubmit`: parse `fieldErrors` from error response and call `showFieldError` per field. Add `.field-error` CSS rule.  
      Files: `js/app.js`, `css/style.css`  
      Verify: Submitting Add Book with a duplicate ISBN shows red error under the ISBN input

- [ ] 19. **Edit Member modal + handler** — No UI to update member details; only Delete exists.  
      Add `id="editMemberModal"` in HTML (mirror editBookModal structure, purple color scheme). Fields: hidden editMemberId, editMemberName, editMemberNum (readonly), editMemberType (select), editMemberEmail, editMemberPhone, editMemberDept. Form `onsubmit="handleEditMemberSubmit(event)"`.  
      Add `openEditMemberModal(member)` and `handleEditMemberSubmit(e)` in `app.js`.  
      In `renderMembersTable()`, add Edit button per row: `onclick='openEditMemberModal(${JSON.stringify(m)})'`.  
      Files: `index.html`, `js/app.js`  
      Verify: Edit button appears in member rows; modal opens pre-filled; submitting updates the member

- [ ] 20. **Member status toggle button** — No UI to activate/deactivate a member.  
      Add Activate/Deactivate button in `renderMembersTable()` row actions: shows "Deactivate" (rose) if active, "Activate" (emerald) if inactive. `onclick="toggleMemberStatus(m.id, m.active)"`.  
      Add `async function toggleMemberStatus(id, currentActive)` in `app.js` calling `PUT /api/members/{id}/status`.  
      Files: `js/app.js`  
      Verify: Clicking Deactivate on an active member refreshes the row with Inactive badge

- [ ] 21. **Member History modal** — No way to view a member's past transactions.  
      Add `id="memberHistoryModal"` in HTML (max-w-3xl, header shows member name, transaction table with 7 columns).  
      Add `async function openMemberHistory(memberId, memberName)` calling `GET /api/transactions/member/{memberId}`, rendering into the modal table.  
      Add History button in `renderMembersTable()` row actions.  
      Files: `index.html`, `js/app.js`  
      Verify: History button opens modal; modal shows all transactions for that member with correct status badges

- [ ] 22. **Return and Renew buttons in transaction log** — ISSUED rows in the transactions table have no inline actions.  
      In `renderTransactionsTable()`, add a 9th Actions column. For ISSUED rows: add Return button (`quickReturn(tx.id)`) and Renew button (`renewBook(tx.id)`). Add `<th>Actions</th>` to the transactions table thead. Update colspan in empty-state td from 8 to 9.  
      Add `async function renewBook(txId)` calling `PUT /api/transactions/{txId}/renew?days=7`.  
      Files: `index.html`, `js/app.js`  
      Verify: ISSUED rows show Return and Renew buttons; clicking Renew shows toast with new due date

- [ ] 23. **Fine payment UI** — No way to mark an assessed fine as paid.  
      In `renderTransactionsTable()`, for RETURNED rows where `fineAmount > 0 && !finePaid`: add Pay Fine button (`payFine(tx.id)`, amber). For `finePaid === true`: show green "Paid" badge.  
      Add `async function payFine(txId)` calling `POST /api/transactions/{txId}/pay-fine`.  
      Files: `js/app.js`  
      Verify: A returned row with a fine shows Pay Fine button; after clicking, it shows Paid badge

- [ ] 24. **Fix quickReturn() async race condition** — quickReturn may try to select a transaction before the return dropdown is populated.  
      Convert `quickReturn()` to `async function`, `await populateReturnDropdown()` before setting `select.value`.  
      Files: `js/app.js`  
      Verify: Clicking Return in dashboard table navigates to transactions tab and pre-selects the transaction

- [ ] 25. **Transaction search** — No search on the transaction log.  
      Add search input `id="txSearchInput"` above the transactions table (in `index.html`).  
      Add debounced `handleTxSearch()` in `app.js` filtering `allTransactionsCache` on `book.title` and `member.name` (case-insensitive, 250ms debounce).  
      Files: `index.html`, `js/app.js`  
      Verify: Typing a member name in the search input filters the transaction rows

- [ ] 26. **Category filter from API** — Category dropdown is hardcoded HTML; new categories don't appear automatically.  
      Add `async function loadCategories()` calling `GET /api/books/categories` and populating `#bookCategoryFilter`.  
      Call `loadCategories()` in DOMContentLoaded.  
      Fix `filterBooksByCategory()` to call `GET /api/books/search?query=<cat>` instead of client-side array filter.  
      Files: `js/app.js`  
      Verify: Category dropdown populates from the API on page load; selecting a category fetches fresh results from server

- [ ] 27. **Overdue detail panel on Dashboard** — Overdue count is shown but there is no drilldown panel.  
      Add collapsible Overdue Detail panel in the Dashboard tab (after secondary stats row): header with toggle button, hidden `id="overdueDetailContent"`, table `id="overdueDetailTable"` (Member, Book, Issue Date, Due Date, Days Overdue, Est. Fine, Return Action).  
      Add `toggleOverduePanel()`, `loadOverdueDetail()` in `app.js` calling `GET /api/transactions/overdue`.  
      Files: `index.html`, `js/app.js`  
      Verify: Clicking the toggle expands the panel and shows overdue book rows with correct day counts and estimated fines

- [ ] 28. **Popular Books panel on Dashboard** — No book demand visibility.  
      Add Popular Books panel on Dashboard (always visible, below overdue panel): table `id="popularBooksTable"` (Rank, Title, Author, Times Issued).  
      Add `async function loadPopularBooks()` calling `GET /api/books/popular`. Call it at the end of `fetchDashboardStats()` success block.  
      Files: `index.html`, `js/app.js`  
      Verify: Dashboard shows top 5 books with their issue counts

- [ ] 29. **Outstanding Fines summary card on Dashboard** — No at-a-glance unpaid fine total.  
      Add a third card to the secondary stats row (change to md:grid-cols-3): amber color scheme, `id="stat-outstanding-fines"`, title "Outstanding Fines", subtitle "Unpaid fine balance".  
      Add `updateOutstandingFines()` computing `sum(fineAmount where !finePaid)` from `allTransactionsCache`. Call it at end of `fetchAllTransactions()` and after `payFine()` success.  
      Files: `index.html`, `js/app.js`  
      Verify: Dashboard card shows correct outstanding fine total; updates after marking a fine paid

- [ ] 30. **CSV Export buttons** — No data export capability.  
      Add Export CSV button on Books tab toolbar (`exportBooksCSV()`). Add Export CSV button on Members tab toolbar (`exportMembersCSV()`).  
      Add `exportTableToCSV(data, filename, columns)` using Blob API. Add `exportBooksCSV()` and `exportMembersCSV()` functions.  
      Files: `index.html`, `js/app.js`  
      Verify: Clicking Export CSV on Books tab downloads `books.csv` with correct headers and data

---

## GROUP 4 — Style & UX Improvements

- [ ] 31. **Dark mode CSS overrides** — Tailwind CDN handles `dark:` utility classes but custom colors and component styles need CSS overrides.  
      In `css/style.css` add: `.dark body`, `.dark .bg-white`, `.dark .bg-slate-50`, `.dark .border-slate-200`, `.dark .text-slate-900`, `.dark input`, `.dark select` overrides. These complement the Tailwind CDN `dark:` classes already added in item 13.  
      Files: `src/main/resources/static/css/style.css`  
      Verify: Dark mode visually applies to all panels including modals

- [ ] 32. **Responsive table improvements** — Tables should scroll horizontally on small screens.  
      Verify all 5 data tables have `overflow-x-auto` on their wrapper div. Add where missing. Confirm stat cards use `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4`.  
      Files: `index.html`  
      Verify: On a narrow viewport (< 640px), tables scroll horizontally without overflow

---

## VERIFICATION ORDER (compile-safe sequence)

Items must be completed in this order to ensure `mvn compile` never breaks:
1. Items 1 (pom.xml) and 2 (port fix) — no Java changes, safe to do together
2. Items 3–5 — Java entity + exception + service changes; compile after item 5
3. Items 6–12 — backend features; each compile after item 12
4. Items 13–32 — all frontend; compile remains green (no Java changes)

Final verification: `mvn test` — all 3 existing tests pass.

---

## File Change Summary

| File | Items |
|---|---|
| `pom.xml` | 1 |
| `application.properties` | 2 |
| `exception/GlobalExceptionHandler.java` | 3 |
| `entity/Book.java` | 5, 7 |
| `entity/Transaction.java` | 6 |
| `dto/MemberStatusRequest.java` (new) | 8 |
| `repository/TransactionRepository.java` | 9 |
| `repository/BookRepository.java` | 9 |
| `service/TransactionService.java` | 10 |
| `service/BookService.java` | 10 |
| `service/MemberService.java` | 10 |
| `service/impl/TransactionServiceImpl.java` | 11 |
| `service/impl/BookServiceImpl.java` | 5, 11 |
| `service/impl/MemberServiceImpl.java` | 11 |
| `controller/TransactionController.java` | 12 |
| `controller/BookController.java` | 12 |
| `controller/MemberController.java` | 12 |
| `static/index.html` | 13, 14, 16, 17, 19, 21, 22, 24, 25, 27, 28, 29, 30, 32 |
| `static/js/app.js` | 4, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30 |
| `static/css/style.css` | 13, 18, 31 |
