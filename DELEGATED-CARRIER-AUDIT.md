# 2.14 Delegated — полный путь временных прав

Исправляет ошибку 2.13 native:carrier-config:SecurityException. Прямой вызов overrideConfig из shell-процесса заменён вызовом от UID нашего приложения с временной shell permission identity, как в изученном Carrier IMS.

## Цепочка

1. После явного подтверждения RegionSession сохраняет UUID/план и вооружает одноразовое разрешение LocalCarrierAuthorization в памяти приложения.
2. Новая служба DelegatedCarrierService через Shizuku проверяет исходную SIM, PID приложения и сигнатуры. Она запускает только собственный CarrierProfileInstrumentation, с INSTR_FLAG_NO_RESTART (8) и системным UiAutomationConnection.
3. Инструментация проверяет PID, точный план и одноразовое разрешение, получает ограниченный Binder gate. Без подтверждения в приложении запись невозможна.
4. HiddenApiBypass/LSPass 6.1 (та же библиотека, что использует проверенный Carrier IMS) открывает доступ к внутренним API процесса. Это не выдача Android-разрешений.
5. UiAutomation.adoptShellPermissionIdentity(MODIFY_PHONE_STATE) делегирует временное разрешение активной инструментации. Публичный API Android использует корректную для данной системы реализацию ActivityManager, включая различия очистки делегации по UID.
6. Под UID приложения выполняется прямой Binder ICarrierConfigLoader.overrideConfig(subId, emptyBundle/null, false). Прямой Binder выбран, чтобы не скрывать RemoteException внутри CarrierConfigManager.
7. Одноразовый gate выполняет setCarrierTestOverride/clearCarrierTestOverride от shell через Shizuku; SIM перепроверяется. Неподдерживаемая очистка использует исходный MCC/MNC и явно отмечает fallback.
8. TemporaryShellPermissionLease освобождает успешно полученное разрешение в finally даже при ошибке записи. При отказе acquire чужая делегация не сбрасывается. finish() завершает нашу инструментацию; при её завершении Android также удаляет её делегацию. Успех показывается только после возврата обоих вызовов и освобождения временных прав.

Watcher принимает результат только от system UID 1000 и для нашего компонента. Gate ограничен app UID, PID, UUID, одним claim и одним вызовом identity. inspect ничего не перезапускает, UUID не повторяется. Новые операции не используют внешний Carrier IMS. Snapshot/APN запись не добавлялись, автоприменения при загрузке нет.

## Переход с 2.13

Старый канал native-v1 оставлен только для проверки прошлого запроса. Точное сочетание OUTCOME_UNCERTAIN + native:carrier-config:SecurityException классифицируется как отказ первого вызова, после чего новые ручные действия доступны. Если старый процесс потерян, приложение не объявляет успех: только после перезагрузки можно явным подтверждением архивировать неизвестный старый запрос 2.13. Эта кнопка не пишет настройки и не повторяет команду. Для новых неопределённых запросов 2.14 такой обход не добавлен.

## Зависимости и источники

Механизм реализован в нашем приложении по проверенному поведению и контрактам AOSP, исходники Carrier IMS не копировались. Нужен только Shizuku и разрешение этому приложению.

- Cached reference: Carrier IMS commit 4312b5a9244a20444cccf5c4a5f763f6fd9d2ab5, ImsModifier/ShizukuProvider/ShellPermissionDelegation.
- AOSP AccessCheckDelegateHelper: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/AccessCheckDelegateHelper.java
- AOSP IActivityManager: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/IActivityManager.aidl
- UiAutomation / UiAutomationConnection: local SDK sources/android-35/android/app.
- HiddenApiBypass 6.1: https://github.com/LSPosed/AndroidHiddenApiBypass (Apache 2.0, license included in app/libs).

Проверка сборки не заменяет испытание на конкретном Android 17. Отдельно проверить apply 222/88 и restore, отсутствие внешнего APK, результат native2 и реальное состояние IMS. Успешный возврат API не гарантирует регистрацию IMS или сохранение эффекта после перезагрузки.
