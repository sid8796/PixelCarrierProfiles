from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
main=root/'app/src/main'
def strings(folder):
 rows=ET.parse(main/'res'/folder/'strings.xml').getroot()
 names=[r.attrib['name'] for r in rows]
 assert len(names)==len(set(names)), 'Duplicate resource keys'
 return {r.attrib['name']:r.text or '' for r in rows}
en,ru=strings('values'),strings('values-ru')
assert set(en)==set(ru), 'Translation coverage mismatch'
for key in en:
 assert sorted(re.findall(r'%\d+\$[.\d]*[sdf]',en[key]))==sorted(re.findall(r'%\d+\$[.\d]*[sdf]',ru[key])), key
 assert not re.search('[\u0400-\u04ff]',en[key]), key
assert [x.attrib['{http://schemas.android.com/apk/res/android}name'] for x in ET.parse(main/'res/xml/locales_config.xml').getroot()]==['en','ru']
labels=(main/'java/com/example/simregionswitcher/CatalogLabels.kt').read_text('utf-8')
regions=dict(re.findall(r'"([^"]+)" to "([A-Z]{2})"',labels))
catalog=(main/'java/com/example/simregionswitcher/OperatorCatalog.kt').read_text('utf-8')
for country,name in re.findall(r'Operator\("([^"]+)", "([^"]+)"',catalog):
 assert country in regions,country
 display=name.replace('бывш.','formerly').replace('другой PLMN','alternate PLMN').replace('Карнатака','Karnataka').replace('Андхра-Прадеш','Andhra Pradesh')
 assert not re.search('[\u0400-\u04ff]',display),name
print(f'PASS: {len(en)} EN/RU strings, unique keys, placeholder parity, no Russian leakage in English, supported locales and catalog display coverage')
