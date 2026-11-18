# МегаФон Россия v1 — экспериментальный профиль

Применяется только к SIM 25002 → 25002, Carrier ID 1016. Не проверен в сети на Pixel. В приложении добавлены карточка оператора, значок, подробности, применение и проверка настроек. Улучшения первого запуска из 2.62 сохранены.

| APN | Тип | Авторизация | Протокол дома / роуминг |
|---|---|---|---|
| internet | default,supl | PAP, gdata/gdata | IPv4/IPv6 / IPv4/IPv6 |
| mms | mms | PAP, mms/mms | IPv4 / IPv4 |
| ims | ims | без переопределения | IPv4/IPv6 / IPv4/IPv6 |
| hos | xcap | без переопределения | IPv4/IPv6 / IPv4/IPv6 |

MMS: MMSC http://mmsc:8002, прокси 10.10.10.10:8080. IMS и XCAP скрыты из списка APN.

Включены флаги VoLTE, VoWiFi, UT и SMS через IMS. USSD предпочитает IMS с возможностью перехода в CS (1). Разрешено отображение 4G/4G+. Это конфигурационные разрешения, работа услуг не подтверждена.

UT: в Samsung Megafon_RU указан ss_domain_setting=PS_ONLY_VOLTEREGIED, APN XCAP=hos. Перенесены флаг UT и имя APN hos. Отдельных XCAP/BSF адресов и портов в найденном блоке нет: они не выдумывались и не переопределялись. Политика требования регистрации IMS оставлена системной.

Видео и голосовая почта оставлены системными. В исследованном Samsung отсутствует явное включение Vilte и mmtel-video для Megafon_RU; в активном блоке Xiaomi 25002 есть только VoLTE/VoWiFi.

Источники: Samsung A576B imsswitch/globalsettings/imsprofile (Megafon_RU) и SER customer.xml; Xiaomi OS3.0.308.0.WPARUXM system/product APN и MiuiCarrierConfigOverlay default_v2.9.7.xml.

Различия источников: Samsung Internet использует gdata/gdata и dual stack. Xiaomi product использует gdata/gdata и IPv4 (домашний протокол пропущен, роуминг IP); Xiaomi system — пустые учётные данные. Выбран вариант Samsung. IMS dual stack дома и в роуминге согласуется с Samsung и Xiaomi system. Сырые блоки Samsung сохранены в outputs/Samsung-Megafon-RU.

CarrierConfig действует до перезагрузки. APN сохраняются. Существующие записи МегаФона не удаляются, конфликтующие записи не переписываются. Остальные четыре профиля PB не менялись. Подмена оператора, разблокировка частот, перенос модемных NV и таймеров не выполняются.
