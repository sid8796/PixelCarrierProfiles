# 2.18 automatic APN follow-up

A per-card/subscription/slot/source identity preference defaults to true. The normal MCC/MNC confirmation captures this setting and discloses the follow-up. Reset never chains APN restoration. Missing snapshots are captured before MCC/MNC through the existing preservation policy.

A frozen snapshot is armed against one exact request UUID and immutable RegionPlan before the profile RPC. Only a validated MODIFIER_REPORTED_SUCCESS consumes it, once. Pending profile results retain the in-memory continuation; app process death loses it, preventing replay on restart. Terminal failure disarms it. No boot action.

The continuation borrows the profile global operation guard. After a bounded 750 ms settling interval it prepares through the existing APN bridge, checks target/card/source mapping and exact saved profile metadata, persists the APN write intent, then consumes one ticket. Multiple/missing matches require manual intervention. Already-selected means no write. Unknown outcomes retain the global guard and expose the existing check action. Profile success and APN result remain separate.

This restores an existing preferred APN selection, not a full APN database backup. Passwords/proxy/authentication are not copied. Carrier configuration can still change later; the immediate verification does not promise persistence or working Internet. Device testing is required.

Earlier APN audit documents describe their respective versions; this document supersedes manual-only restoration for the confirmed normal MCC/MNC action in 2.18.
