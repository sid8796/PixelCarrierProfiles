"""Generate selectable catalog solely from standalone archive profiles and carrier_list.pb mappings."""
from pathlib import Path
import zipfile,json,hashlib,collections,re,sys
root=Path(__file__).resolve().parents[1]
archive=Path(sys.argv[1])
def fields(b):
 pos=0;out=[]
 def vi():
  nonlocal pos
  value=0;shift=0
  while True:
   assert pos<len(b) and shift<=63
   x=b[pos];pos+=1;value|=(x&127)<<shift
   if x<128:return value
   shift+=7
 while pos<len(b):
  t=vi();w=t&7
  if w==0:value=vi()
  elif w==2:
   n=vi();value=b[pos:pos+n];pos+=n;assert len(value)==n
  else:raise ValueError(w)
  out.append((t>>3,value))
 return out
with zipfile.ZipFile(archive) as z:
 names={Path(n).stem:n for n in z.namelist() if n.endswith('.pb')}
 eligible=set(names)-{'others','carrier_list','default','no_sim'}
 carrier=z.read(names['carrier_list']);pairs=collections.defaultdict(list)
 for tag,value in fields(carrier):
  if tag!=1:continue
  record=fields(value);name=next((v.decode('utf-8') for f,v in record if f==1),None)
  if name not in eligible:continue
  for field,data in record:
   if field!=2:continue
   identity=fields(data);numeric=next((v.decode('ascii') for f,v in identity if f==1),None)
   if numeric and re.fullmatch(r'[0-9]{5,6}',numeric):pairs[name,numeric].append(any(f!=1 for f,v in identity))
 assert {p for p,n in pairs}==eligible
 rows=[]
 for (name,numeric),conditions in pairs.items():
  suffix=name.rsplit('_',1)[-1]
  country=suffix.upper() if re.fullmatch('[a-z]{2}',suffix) and suffix not in {'zz','xx'} else 'ZZ'
  rows.append(dict(country=country,name=name,mcc=numeric[:3],mnc=numeric[3:],profile=name,conditional=all(conditions)))
 rows.sort(key=lambda x:(x['country'],x['name'],x['mcc'],x['mnc']))
 countries=sorted({x['country'] for x in rows})
 manifest=dict(source='CarrierSettings-files.zip',archive_sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),carrier_list_sha256=hashlib.sha256(carrier).hexdigest(),excluded=['others.pb','default.pb','no_sim.pb','carrier_list.pb (mapping only)'],profile_count=len(eligible),country_count=len(countries),entry_count=len(rows),entries=rows)
(root/'profiles/archive-catalog.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
q=lambda s:json.dumps(s,ensure_ascii=False)
lines=['package com.example.simregionswitcher','', '/** Generated from standalone CarrierSettings archive profiles; others.pb excluded. */','object OperatorCatalog {','    data class Operator(val country:String,val name:String,val mcc:String,val mnc:String,val profile:String,val conditional:Boolean)','    const val checkedOn = "2026-10-28"',f'    const val profileCount = {len(eligible)}',f'    const val archiveSha256 = "{manifest["archive_sha256"]}"']
chunks=[rows[i:i+80] for i in range(0,len(rows),80)]
lines+=['    val operators = listOf('+','.join(f'part{i}()' for i in range(len(chunks)))+').flatten()']
for i,chunk in enumerate(chunks):
 lines+=[f'    private fun part{i}() = listOf(']
 lines += ['        Operator('+', '.join(q(x[k]) for k in ['country','name','mcc','mnc','profile'])+', '+str(x['conditional']).lower()+')'+(',' if n<len(chunk)-1 else '') for n,x in enumerate(chunk)]
 lines+=['    )']
lines+=['    fun findSelectable(mcc:String,mnc:String):Operator? = operators.firstOrNull { it.mcc==mcc && it.mnc==mnc && !it.conditional } ?: operators.firstOrNull { it.mcc==mcc && it.mnc==mnc }','    fun find(mcc:String,mnc:String):Operator? = findSelectable(mcc,mnc)','    val countries = operators.map { it.country }.distinct().sorted()','}']
java=root/'app/src/main/java/com/example/simregionswitcher'
(java/'OperatorCatalog.kt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
labels='''package com.example.simregionswitcher
import android.content.Context
import java.util.Locale

/** Country display only; canonical profile names and codes are preserved from the archive. */
object CatalogLabels {
    private val regions=mapOf(
'''+',\n'.join('        '+q(c)+' to '+q(c) for c in countries)+'''
    )
    fun country(context:Context,key:String):String {
        val locale=context.resources.configuration.locales[0]
        return if(key=="ZZ") context.getString(R.string.catalog_international) else regions[key]?.let { Locale("",it).getDisplayCountry(locale).ifBlank { key } } ?: key
    }
    fun operator(context:Context,name:String):String = name
}
'''
(java/'CatalogLabels.kt').write_text(labels,encoding='utf-8')
print('Generated',len(rows),'entries,',len(eligible),'profiles,',len(countries),'country/region groups')
