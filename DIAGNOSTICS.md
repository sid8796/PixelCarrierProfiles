# Persistence unavailable on the tested Pixel firmware

2.43.2 restores temporary CarrierConfig apply/reset. Keep after reboot is disabled, new requests always use persist_config=false, and both helper and instrumentation reject persistent requests before APN or CarrierConfig writes. Old request history keeps its original choice. A manual acknowledgement is offered only for explicit returned CarrierConfig SecurityException refusals of the local profile; it sends no phone command. Timeouts and lost-reply locks are unchanged. No automatic fallback or boot replay.

TeleService.apk supplied by the user contains CarrierConfigLoader.secureOverrideConfig(PersistableBundle, boolean). It rejects shell callers and rejects persistent=true on user builds unless isSystemApp() succeeds. isSystemApp checks packages of the real Binder caller for ApplicationInfo flags 1 (system) or 128 (updated system). Adopting MODIFY_PHONE_STATE does not make this app a system app.

The previous 2.43 policy called overrideConfig(subId, null, true) first, which also triggers this restriction. Disabling the switch in 2.43 did not avoid it. 2.43.2 uses only persistent=false. Reset removes runtime overrides and leaves APNs and any pre-existing system-created persistent configuration untouched. 2.43 did not establish persistent overrides on this device.

Diagnostic reason64 logging remains available. A physical phone test is still required to confirm temporary apply/reset after this fix.
