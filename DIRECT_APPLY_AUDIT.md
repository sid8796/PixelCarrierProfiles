# Direct ImsModifier path — 2.12

UI requestProfileAction only shows confirmation. Both positive handlers recheck the still-selected plan and canApplyDirect, then call RegionSession.applyDirect. There are no UI calls to RegionSession.prepare or the old apply method.

runWork("direct") acquires the shared request gate; stores NOT_SENT plus direct=true and immutable plan; checks access/current mapping; binds a dedicated sim-region-carrier-direct / version 1 helper; commits APPLY_INTENT_OUTCOME_UNKNOWN and UUID synchronously before applyDirect RPC. It does not read/require a dump, snapshotPath or ticket. Replies are validated against ID, phase=apply and exact echoed plan, then use the existing history and recovery rules.

ICarrierTrial adds transaction 3 applyDirect(requestId, Bundle plan). Existing transaction IDs 0-2 are unchanged, so old normal/restore pending inspection keeps its old tags/version (normal 3, restore 1). New direct=true is persisted with the request and selects only the new helper; cache identity includes the channel. Read-only and APN AIDL/backends are unchanged.

CarrierTrialService.applyDirect validates caller UID, UUID and RegionPlan. DirectApplyRules decides START, OBSERVE or BLOCK. The same UUID with matching plan/apply phase returns cached/completion state and never invokes again; changed plan/phase blocks. A fresh UUID cannot bypass an unresolved or uncertain prior attempt. Only the START branch checks pinned package/components, checks ISub mapping and invokes the fixed MODIFIER component once, with RegionActionRules typed payload. It never invokes CONFIG. All old late-observer, result envelopes and no-retry behavior remain.

Normal mode passes select_sim_id:Int, country_mcc_override:String, country_mnc_hint:String. Restore passes select_sim_id:Int and reset:Boolean=true. There is no component/command/all-SIM selection supplied by a caller. UI keeps restore source identity distinct from desired MCC/MNC.

16 new pure JVM cases exercise direct UUID reuse, changed target/phase, pending/uncertain blocking and known completion allowing a fresh manual action. Combined with prior payload/scope/recovery checks, total 555. Runtime Binder/Android behavior and actual phone latency still require device testing. No device write was performed by builds/tests.

RESTORE_AUDIT.md and TRIAL_AUDIT.md describe prior versions. Their snapshot requirement is superseded by this document for new direct operations; the old path remains only for compatibility with earlier pending work.
