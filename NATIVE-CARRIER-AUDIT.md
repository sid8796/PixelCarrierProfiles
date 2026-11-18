# 2.13 Standalone: собственный исполнитель профиля

Новая реализация NativeCarrierService независимо обращается к системным AOSP Binder-интерфейсам из процесса Shizuku с shell/root identity. Исходники ImsModifier/Carrier IMS не включены и не копировались. Не нужны внешний APK, instrumentation, делегирование разрешений другому UID или разделённый экран.

Применение, только для выбранного действительного subId:
1. Проверить точные сигнатуры API и соответствие slot/subId/source MCC/MNC/embedded.
2. ICarrierConfigLoader.overrideConfig(subId, пустой PersistableBundle, false).
3. Повторно проверить соответствие SIM.
4. ITelephony.setCarrierTestOverride(subId, MCC+MNC, "", "", "", "", "", "", null, null).

Восстановление:
1. Те же предварительные проверки.
2. ICarrierConfigLoader.overrideConfig(subId, null, false).
3. Повторная проверка SIM.
4. ITelephony.clearCarrierTestOverride(subId), если существует точная сигнатура.
5. Иначе setCarrierTestOverride с исходным MCC/MNC; результат явно отмечает fallback и не обещает полного снятия тестовой подмены.

Системные вызовы не сохраняют override как persistent. Успешный возврат обоих вызовов не означает, что сеть уже переподключилась или IMS зарегистрировался. Системная конфигурация может сама изменить APN; собственных записей APN здесь нет. Автовыполнения при загрузке/выборе SIM нет.

Один подтверждённый UUID запускается один раз, в рабочем потоке. applyDirect немедленно возвращает текущий статус; inspect только читает его. Повтор того же UUID не повторяет записи. Маркер неопределённого исхода сохраняется до RPC. При исключении после входа в первый Binder write исход считается неопределённым, автоматического повтора нет. При потере процесса inspect возвращает REQUEST_NOT_TRACKED, приложение не пытается повторять запись. Старые каналы CarrierTrialService оставлены исключительно для чтения результата старого незавершённого запроса после обновления; новые кнопки к ним не обращаются.

Устаревшие тестовые чтения IMS/ConfigReader через внешний APK скрыты. Основной статус IMS, возможности и приватный номер по-прежнему читаются собственным MmTel reader. Кнопка открытия Carrier IMS удалена. APN остаётся замороженным в тестовом разделе.

API проверены по локальному SDK sources/android-35/android/telephony/TelephonyManager.java (9 строковых параметров setCarrierTestOverride) и CarrierConfigManager.java (overrideConfig с subId/bundle/persistent), а также доступным интерфейсам AOSP:
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/com/android/internal/telephony/ITelephony.aidl
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/com/android/internal/telephony/ICarrierConfigLoader.aidl

Проверка: сборка APK, Android lint и 601 JVM проверка, включая 46 случаев последовательности native-транзакции: apply/reset, ошибки preflight, обе проверки SIM, первый/второй write и отсутствие повторов. На реальном устройстве новая реализация ещё не проверена. Для скрытых API любой неподдерживаемый набор сигнатур должен завершиться PRECHECK_FAILED до записи.
