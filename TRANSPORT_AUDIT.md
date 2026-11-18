# Аудит чтения транспорта IMS для SIM Region Switcher 2.3

Проверено 23 октября 2026. Цель — чтение состояния и транспорта, без изменения IMS.

## Доступные данные
В проверенной версии Carrier IMS 3.9.1.r107.4312b5a9 ImsStatusReader вызывает
ITelephony.isImsRegistered(subId) и возвращает Boolean. Транспорт, номер и IMS
identity в его ответ не включены. ConfigReader читает параметры конфигурации,
которые не подтверждают транспорт реальной регистрации.
Исходник: https://github.com/ryfineZ/carrier-ims-for-pixel/blob/4312b5a9244a20444cccf5c4a5f763f6fd9d2ab5/app/src/main/java/io/github/vvb2060/ims/privileged/ImsStatusReader.kt
Локальная проверенная копия находится в рабочем carrier-audit; старый бинарник
Carrier IMS не изменяется и не переупаковывается.

Android ImsMmTelManager предоставляет getRegistrationState и
getRegistrationTransportType. В изученном SDK 36.1 эти обёртки вызывают
ITelephony.getImsMmTelRegistrationState и getImsMmTelRegistrationTransportType.
Штатная обёртка может подставлять NOT_REGISTERED/INVALID при ошибке IPC;
реализация 2.3 использует Binder getter непосредственно и сохраняет ошибку.

Системный сервис проверяет права: state — READ_PRIVILEGED_PHONE_STATE,
transport — precise phone state / carrier privilege (с платформенной проверкой).
Попытка выполняется от shell/root Shizuku UserService, не от обычного app UID.
Разрешения не выдаются командами pm grant, приложение не меняет AppOps.
Отказ фиксируется как PLATFORM_ACCESS_DENIED, без fallback записи или сброса.

Состояния Android: 0 Not registered, 1 Registering, 2 Registered.
AccessNetworkConstants: -1 INVALID, 1 WWAN, 2 WLAN.
RegistrationManager.IMS_REG_TO_ACCESS_TYPE_MAP сводит LTE и NR к WWAN,
IWLAN и CROSS_SIM к WLAN. Обратное восстановление точной технологии невозможно.
Нельзя по одному WLAN утверждать, что транспорт физически идёт через Wi-Fi,
или по WWAN исключать VoNR. Нельзя подтверждать тип активного звонка.

Состояние читается до и после транспорта. При разнице — IMS_STATE_CHANGED.
Даже равенство не делает три последовательных запроса атомарным снимком.
Смена транспорта возможна между ответами; это явно указано в UI и README.
Слот/subId сверяются через ISub до и после; старые результаты не переименовываются.

## Узкая поверхность нового кода
Фиксированный helper.readTransport(slot, subId), проверка caller UID,
положительного/нулевого subId и допустимого слота. Новый код не вызывает
startInstrumentation, startDelegateShellPermissionIdentity, overrideConfig,
setCarrierTestOverride, IMS reset, radio setters, APN API или shell command.
Три последовательных одноразовых callback-запроса (для незарегистрированного
состояния transport не вызывается). Тайм-аут ожидания каждого callback 4 секунды.
Не создаёт постоянных слушателей и не запускает Carrier IMS.

Ошибки/тайм-ауты/отсутствующие методы не преобразуются в известный результат.
Старые IMS/CarrierConfig paths и новый MmTel path отделены по ключу операции,
слоту/subId и времени. Callback нового транспорта не может завершить другой запрос.

## Источники Android
- ImsMmTelManager: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/ims/ImsMmTelManager.java
- PhoneInterfaceManager, Android 17: https://android.googlesource.com/platform/packages/services/Telephony/+/android17-release/src/com/android/phone/PhoneInterfaceManager.java
- ITelephony: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/com/android/internal/telephony/ITelephony.aidl
- RegistrationManager (таблица преобразования): https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/ims/RegistrationManager.java
- IIntegerConsumer контракт: https://android.googlesource.com/platform/frameworks/base/+/09f6675744839dfd59b218832cdb4dc3fe435d75/telephony/java/com/android/internal/telephony/IIntegerConsumer.aidl
Также сверены локальные исходники SDK android-36.1: ImsMmTelManager,
RegistrationManager, AccessNetworkConstants и TelephonyServiceManager.

Статические исходники описывают доступный механизм, но не гарантируют его
реализацию и разрешения на конкретном устройстве API 37. Проверка runtime нужна.
