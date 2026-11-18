# 2.8 dual-SIM mutation audit

## Scope

RegionActivity is the launcher. MainActivity remains an internal diagnostics activity, with its old validation fields and fixed A1 mutation controls removed. Selecting a card or editing fields does not call the helper. Positive confirmation invokes only the prepared immutable plan currently displayed.

RegionSession replaces A1TrialSession while retaining its private preference file to preserve all old pending/apply intent markers. Completed known outcomes permit a new explicit preparation; an unknown result does not. There is no automatic apply after restart, after inspection or after selecting a SIM.

## Target binding

RegionPlan binds slot 0 or 1, nonnegative Android subscription ID, operator display label, observed source MCC/MNC, embedded flag, and requested MCC/MNC. Validation uses ASCII digits with MCC exactly 3 and MNC 2 or 3; strings preserve leading zeros. The operator label is descriptive; app/helper scope checks use slot, subscription ID, source MCC/MNC and embedded flag. They do not store IMSI/ICCID.

The app checks current SubscriptionManager data before preparation and apply. The helper separately validates ISub.getSubId and getActiveSubscriptionInfo using strictly typed supported signatures, including immediately before instrumentation. The checks are sequential, not atomic with SIM swaps. The immutable prepared plan is echoed with every result and checked with request ID and phase before accepting a reply.

ICarrierTrial.prepare(requestId, Bundle plan) decodes only the eight fixed plan fields. There is no generic component/configuration/shell command API. Apply accepts only the single-use preparation ticket. Shizuku user-service version is 3, so an old fixed A1 helper cannot receive the new plan interface silently.

Only fixed ConfigReader / ImsModifier components in reviewed Carrier IMS 3.9.1.r107.4312b5a9 / 107 are invoked, with NO_RESTART (8), correct app user and private component-checked watcher. Selection is always putInt(select_sim_id, plan.subId), never -1 or a String. Desired MCC and MNC are String fields from the validated plan. Legacy A1TrialRules constants remain for regression checks/component allowlisting/ticket lifetime; no production path uses its fixed SUB/SLOT/MCC/MNC selection.

## Transaction lifecycle

An explicit new preparation first stores a local NOT_SENT checkpoint for its plan, so a preflight failure cannot retain an unrelated completed apply lock. A locked PREPARING_INTERRUPTED marker precedes ConfigReader IPC. The full available effective config dump is written using AtomicFile, and only then is a ticket exposed in the app.

A synchronous APPLY_INTENT_OUTCOME_UNKNOWN marker for the same plan precedes helper.apply IPC. Definite modifier completion is archived independently per slot and subscription ID with time. A definite response is not treated as proof of registration or call transport. A partial mutation remains possible on modifier error.

Late callbacks use immutable request-scoped atomic completion slots. Inspection only reads the old slot and finalizes a read snapshot; no new instrumentation or modification is started. The prepared ticket/snapshot expires; inspecting it does not renew it. Unknown outcomes keep a shared global request gate, so another SIM cannot bypass uncertainty. Reboot recovery still clears only preparation, not an unknown apply intent.

A new explicit prepare is allowed after a known ended operation, including after a successful prior mutation, and requires a new snapshot/confirmation. The helper resets its attempt flag only at such a new preparation; unresolved or uncertain operations remain locked. User-visible history is dated and explicitly separate from current subscription/IMS state.

## Limits

Source reviewed from ryfineZ/carrier-ims-for-pixel commit 4312b5a9244a20444cccf5c4a5f763f6fd9d2ab5. Its modifier performs empty CarrierConfig override then test identity override, so APN selection and other carrier behavior can change. No automatic APN restore, generic reset, persistence request, radio reset or boot receiver is added. Snapshot is comparison data, not exact rollback.

No device writes happen in build or unit checks. 296 JVM cases include 43 new dual-SIM profile/scope/intent checks. Actual Binder behavior, OEM freeze handling, screen appearance and SIM 2 mutation require device testing. Existing read-only diagnostic bridge/protocol is unchanged.

## 2.9 presentation-only migration

Privileged RegionSession/RegionPlan/RegionPlanCodec/CarrierTrialService and the read-only bridge/session/number source files are byte-identical to 2.8. The launcher remains RegionActivity, now with a light theme, native insets, country/operator selectors and compact two-column radio summaries. MainActivity only adds an optional openStatus intent from the bottom Diagnostics SIM button; it remains non-exported.

Imported from the user-provided 1.2.1 archive: OperatorCatalog (41 entries), visual layout concepts and the finite public radio network-family mapping. The confirmed A1 257/01 entry is added. No ShellUserService, embedded modifier, generic command transport or reset code was imported. Catalog selection populates desired strings only, never invokes preparation or apply. The existing plan/ticket binding rejects an edited profile or a different subscription.

RadioSummary uses guarded public per-subscription data/voice RAT and signal getters, without location permission or ServiceState collection. IWLAN is not labelled cellular LTE. Invalid/missing dBm measurements and levels are rejected by pure display rules. Values update every 5 seconds only while the Activity is visible; this is separate from the existing pending-request inspection loop, which never restarts instrumentation.

The bottom API check calls the existing read-only TRANSPORT operation with an observed slot/subscription mapping. Journal output contains event codes and requested MCC/MNC/slot, never subscriber IMS URI or phone number. Existing detailed diagnostics and number privacy remain intact.

New art was generated specifically for 2.9, saved in the project and packaged into adaptive launcher resources. It does not load at runtime from the internet or from Codex paths. The generated master is preserved in artwork/launcher-source.png.

465 pure JVM checks now pass. 169 new checks cover all 42 catalog records, country membership, exact leading-zero lookup and radio display handling, including rejection of IWLAN/unknown RAT/sentinel signal values. These do not emulate Android Spinner event ordering or replace a device visual test. APK build and lint pass; 0 errors, 31 warnings. No device modification is performed by these checks.
