# Catalog sources — checked 2026-10-24

Scope: 33 Pixel 10 sales/program regions; curated major public PLMNs, not every carrier/MVNO or every regional allocation. Regional program eligibility and repair support corroborate partner markets beyond the direct Google Store list; this is not a claim that every model/accessory is sold in every region. India entries explicitly identify selected circles. No automatic choice of a donor IMS profile.

## Countries and market coverage

- Direct Google Store sales (24 regions): https://support.google.com/store/answer/2462844?hl=en
- Pixel 10 regional offer (33 unique regions, UK duplicated in source): https://one.google.com/offer/terms-and-conditions/pixel10-6month-2tb
- Google regional support corroboration: https://support.google.com/pixelphone/answer/10227672?hl=en
- Mexico official launch: https://blog.google/intl/es-419/actualizaciones-de-producto/dispositivos/google-pixel-llega-a-mexico-con-lo-mejor-de-la-ia-integrada/
- Malaysia official operator sales: https://corporate.celcomdigi.com/news/celcomdigi-opens-pre-orders-for-google-pixel-10-series
- Estonia retailer/operator: https://pood.telia.ee/tooted/Google-Pixel-10-128-GB-helesinine/GA10214-GB
- Latvia retailer/operator: https://www.lmt.lv/veikals/visi-telefoni/google-pixel-10

## Codes

Every included MCC+MNC occurs in the fetched AOSP carrier ID data. This verifies an Android code entry, not Pixel IMS certification, network status or a brand's current provisioning policy.

Source: https://raw.githubusercontent.com/aosp-mirror/platform_packages_providers_telephonyprovider/main/assets/latest_carrier_id/carrier_list.textpb
Gitiles: https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/main/assets/latest_carrier_id/carrier_list.textpb
Fetched file SHA256: a6a8cc44afb9ad091bda816da98e546c0d8eac86851ee28656ee6802e5ca0825

Individual code/alias matches appear in operators.csv. All numeric cells must be treated as strings. In particular 334/020 differs from 334/20; MNC length is preserved. Legacy A1 BY and Korean lookups remain internal to recognize previous drafts; those countries are not presented as official sales regions. Existing fields for removed catalog regions switch to manual entry without changing their digits.

ITU cross-check: https://www.itu.int/dms_pub/itu-t/opb/sp/T-SP-OB.1280-2023-OAS-PDF-E.pdf
ITU amendments: https://www.ituob.org/recommendations/E.212/
4ka official APN/code reference (231/03; do not confuse 231/08 Uniphone): https://www.4ka.sk/nastavenia-zariadeni/
ice uses the AOSP smartphone carrier tuple 242/14; 242/06 is not used as its generic phone profile.

## Trade names

- Norlys / Telia: https://via.ritzau.dk/pressemeddelelse/14375349/nu-sker-det-telia-bliver-til-norlys?lang=da&publisherId=13559467
- One / Vodafone Hungary: https://www.one.hu/onerol
- Odido / T-Mobile Netherlands: https://newsroom.odido.nl/en-us/t-mobile-netherlands-becomes-odido/
- CelcomDigi: https://corporate.celcomdigi.com/news/press
- SIMBA / TPG: https://www.digitalforlife.gov.sg/about/our-partners/simba

## Support claims

5G: https://support.google.com/pixelphone/answer/10082009?hl=en
Wi-Fi calling: https://support.google.com/phoneapp/answer/2811843?hl=en
Example of an operator's own explicit VoLTE device list: https://www.telstra.com.au/support/mobiles-devices/enable-volte-mobile-phone
No global VoLTE/VoWiFi certification marks were inferred from the sales-region list or AOSP data.
