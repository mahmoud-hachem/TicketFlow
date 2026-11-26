# Part 6 — Code quality, metrics and refactoring

Measured on 26 November 2026. This work preserves the TIC-10 changes, approved requirements, D1–D25, R1–R4 and I6-01–I6-12. No database-dependent tests, application backend, MongoDB connection, commit or push was executed.

## Approved decisions and trade-offs

| Decision | Implementation | Trade-off / alternative |
|---|---|---|
| Q1: JaCoCo line and branch coverage | Pinned Maven plugin, isolated-test instrumentation and XML/HTML reports | Execution coverage is not correctness or persistence evidence. Exception handling is not counted as JaCoCo branches. |
| Q2: Focused PMD and CPD | CyclomaticComplexity, CognitiveComplexity, NcssCount, ExcessiveParameterList; Java production CPD at 100 tokens | A focused ruleset limits noise. Short or structurally similar duplication can remain undetected. SpotBugs and broader style rules are deferred. |
| Q3: Shared frontend mutation orchestration | `useTicketMutation` owns request identity/abort cleanup, submission lifecycle, safe field extraction, reload handling and feedback state | Callers retain validation, response predicates, success actions and operation-specific messages/reload policy. A generic workflow component would hide API differences. |
| Q4: Preserve state architecture | App uses a small synchronous ref/state update helper; the common ticket-update handler has an accurate name; unused status callback removed | All 15 state hooks and selection refs remain. A reducer, presentation rewrite or external state library would expand regression risk. |
| Q5: Focused backend helpers | Shared request-version validation, load/current-version checks and backend timestamp integrity checks | Helpers remain private service orchestration. Domain business rules, validation order, mapping preflight and repository optimistic locking remain unchanged. |
| Q6: Advisory metrics | No coverage or complexity build thresholds; PMD/CPD report goals are explicit | Report or instrumentation failures still fail their execution; findings do not establish a quality gate. Human review remains necessary. |

## Tool versions and reproducibility

- Maven 3.10.0; execution JDK 24.0.2; project Java compilation target 21.
- JaCoCo Maven plugin **0.8.14**. See [release history](https://www.jacoco.org/jacoco/trunk/doc/changes.html) and [counter definitions](https://www.jacoco.org/jacoco/trunk/doc/counters.html).
- Maven PMD plugin **3.28.0**, whose pinned distribution uses **PMD/CPD 7.17.0** (also recorded in generated XML). See [plugin engine version](https://maven.apache.org/plugins/maven-pmd-plugin/examples/upgrading-PMD-at-runtime.html).
- Node **24.20.0**, npm **11.19.0**, installed ESLint **10.15.0**, Vite **8.3.8**. Lockfiles and application dependencies were not changed.
- Mock browser checks use an existing external Playwright installation and installed Microsoft Edge; no project test dependency was installed. Set `TICKETFLOW_PLAYWRIGHT_PATH` to that installation when it is not otherwise resolvable. `TICKETFLOW_BROWSER_CHANNEL` defaults to `msedge`.

Run from `backend`:

```powershell
mvn -ntp test pmd:pmd pmd:cpd
python scripts/quality_metrics.py
```

The ordinary isolated Surefire exclusions remain intact: `BackendApplicationTests` and `*IT` are excluded. **Do not enable `mongo-integration` without separate explicit database authorization.** JaCoCo adds no application dependency. PMD executes only when its report goals are requested. Coverage includes every production class; there are no cosmetic coverage exclusions.

Run from `frontend`:

```powershell
node scripts/quality-metrics.mjs
npm run lint
npm run build
node scripts/mocked-browser.cjs
```

The browser script serves the production `dist` directory on an ephemeral loopback port and intercepts **all** `/api/**` requests with in-memory responses. It neither starts the Java application nor contacts a real backend. `TICKETFLOW_FRONTEND_URL` optionally selects an already running frontend; keep it unset for the self-contained production-build verification.

Before editing production files, frontend metrics and baseline JaCoCo/PMD/CPD XML were captured under `backend/target/quality/before`. The same scripts and rules generated the after measurements under `backend/target/quality/after`. Those generated artifacts are local and ignored by Git; cleaning `target` removes the snapshots. The baseline Maven test/coverage goals passed; its first PMD attempt failed because `ExcessiveMethodLength` is unavailable in PMD 7. It was replaced by `NcssCount`, then the baseline PMD/CPD run passed **before backend refactoring**. Afterward the complete combined Maven command passed.

## Before / after measurements

| Indicator | Before | After |
|---|---:|---:|
| JaCoCo Java lines covered / total | 414 / 423 (97.87%) | 413 / 422 (97.87%) |
| JaCoCo Java branches covered / total | 167 / 180 (92.78%) | 153 / 164 (93.29%) |
| PMD method/constructor complexity total | 211 | 202 |
| PMD method/constructor complexity maximum | 16 | 16 |
| PMD measured methods/constructors | 85 | 88 |
| PMD other focused rule findings | 0 | 0 |
| Java CPD clone groups, minimum 100 tokens | 0 | 0 |
| Java CPD duplicated line occurrences | 0 | 0 |
| ESLint classic frontend complexity total | 316 | 282 |
| ESLint classic frontend complexity maximum | 40 | 33 |
| Frontend measured functions | 75 | 80 |
| Java production files / physical lines | 32 / 1,292 | 32 / 1,297 |
| Frontend source files / physical lines | 7 / 1,187 | 8 / 1,154 |
| App physical lines / state hooks | 568 / 15 | 568 / 15 |
| Standard ESLint errors / warnings | 0 / 0 | 0 / 0 |
| Isolated backend tests passed | 303 | 303 |
| Isolated failures / errors / skipped | 0 / 0 / 0 | 0 / 0 / 0 |

Source counting recursively includes `.java` in `backend/src/main/java` and `.js`, `.jsx`, `.css` in `frontend/src`; blank lines and comments are counted. Tests, analysis scripts, generated files, dependencies and assets are excluded from production counts. App state hooks are literal `useState(` calls. Frontend complexity uses ESLint's **classic** algorithm with an in-memory reporting level of zero; this is a measurement mode, not a configured lint threshold. Totals include callbacks and JSX/optional-chain decisions. Java complexity is PMD's per-method/constructor cyclomatic count, reported from level one; it is not numerically interchangeable with ESLint or JaCoCo complexity. CPD counts exact qualifying token clones, without claiming an overall duplication percentage.

Coverage denominators changed because duplicate branches were consolidated; the modest percentage improvement is not evidence of new behavioral coverage. Added helper functions increase baseline complexity contributions, yet totals decrease across the complete production scope. The shared frontend request runner measures 19; operation handlers measure 3 (assignment), 2 (status) and 7 (comment submission). Assignment component rendering remains the frontend maximum of 33. Ticket's coherent `changeStatus` domain method remains Java's maximum of 16. Complexity was not reduced merely by moving each old handler unchanged.

## Code smells and applied refactorings

- Repeated fetch, response parsing, abort handling, mutation identity checks, lock release, conflict/network feedback and submission state now have one implementation. Operation-specific validation, 201 comment response checks, complete ticket response checks and success/draft actions remain visible in the components.
- Repeated service client-version validation, stored-version checks and clock-integrity checks now use named private helpers. Assignment still preflights comments/detail integrity before agent lookup or mutation; stale versions fail before mutation; returned versions still come from repository results. No automatic integrity repair or identifier normalization was introduced.
- App's duplicated paired workflow ref/state updates now use one synchronous helper. Selection guards, draft ownership, stale-read aborts, list/detail synchronization and manual reload/retry are preserved.
- Existing typed exception handling already distinguishes input and internal errors and preserves framework status/headers. It was reviewed and retained; no global JSON changes or unrelated handler refactoring were justified.
- Existing parameterized regression assertions and prepared Mongo integration tests were retained. A repeatable mocked browser script now covers the extracted lifecycle instead of relying solely on transient manual scripts.

## Actual verification

- Both baseline and post-refactor isolated backend runs passed **303 cases**, **zero failures, errors or skipped tests**. Production and all test sources compile. No existing test source or assertion was edited in Part 6.
- Post-refactor `mvn -ntp test pmd:pmd pmd:cpd`: **BUILD SUCCESS**. JaCoCo XML/HTML and PMD/CPD XML/HTML were generated. Advisory method complexity entries are measurements, not 88 defects; other configured PMD rules reported zero findings.
- Frontend ESLint: **passed**, zero errors/warnings. Production build: **passed**.
- Production-build mocked browser suite: **16 scenario groups passed**: list; creation/list refresh; details/assignee prerequisite; assignment/reassignment/list synchronization; status; comment creation/draft clearing; validation feedback; comment conflict reload/manual retry/draft preservation; network ambiguity; malformed success; shared mutation lock/late mutation response; late detail response; reopening/comment preservation/CLOSED protection; 375px overflow/no runtime exceptions; assignment conflict recovery; status conflict recovery. Every mutation checks that the submitted version matches the mock's current persisted version.
- Browser launch first failed under sandbox process restrictions; an authorized process launch succeeded. A separate sandboxed Vite server was unreachable across the process boundary, so the harness was corrected to serve the built assets in its own process. These were verification-environment corrections, not production API changes.

## Remaining limitations and deferred work

Real persistence, application context startup and competing MongoDB writes remain unverified and explicitly deferred to the guarded TIC-10 database suite. Mocked responses cannot establish real network/database behavior. No new backend tests were needed because existing tests exercise the extracted version and integrity paths; the 303-case suite remains unchanged.

Advisory metrics are signals, not proof of maintainability or business correctness. Short duplication remains despite zero 100-token CPD groups. App remains large and uses 15 independent state hooks; broader decomposition or a reducer remains deferred under Q4. Narrow logging deliberately limits diagnostic detail to protect sensitive information. Further test-fixture consolidation is deferred to avoid obscuring existing regression assertions. Coverage thresholds, broader static-analysis rules, SpotBugs and a project-managed browser-test dependency require separate human decisions. The external browser tool path is an environment-specific prerequisite, not a portable project dependency.
