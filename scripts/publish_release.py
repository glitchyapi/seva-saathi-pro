#!/usr/bin/env python3
"""Generate a signed, content-addressed release; invoked by the owner-only Actions workflow.
No private keys, tokens, or journal entries are published. Use MODE=bootstrap for first setup.
"""
from pathlib import Path
import base64, datetime, hashlib, json, os, re, subprocess, urllib.request
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ROOT = Path(__file__).resolve().parents[1]
OWNER, REPO = 'glitchyapi', 'seva-saathi-pro'
MODE = os.environ.get('RELEASE_KIND', 'interface')
from onlyfiles import OnlyFiles
onlyfiles = OnlyFiles(ROOT)
PRIVATE = os.environ.get('UPDATE_PRIVATE_KEY')
if not PRIVATE:
    PRIVATE = Path(os.environ.get('UPDATE_KEY_FILE', '/home/user/.seva-keys/update-private.pem')).read_text()
key = serialization.load_pem_private_key(PRIVATE.encode(), password=None)
public = serialization.load_pem_public_key((ROOT/'update-public.pem').read_bytes())
if key.public_key().public_numbers() != public.public_numbers():
    raise SystemExit('Signing key does not match the app-pinned public key.')

def canonical(obj): return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()
def sha(b): return hashlib.sha256(b).hexdigest()
def api(path, data=None, method='GET', raw=None, content_type='application/json'):
    token = os.environ.get('GH_TOKEN', '')
    if not token: raise RuntimeError('GH_TOKEN is required to publish an Android release.')
    body = raw if raw is not None else (canonical(data) if data is not None else None)
    url = path if path.startswith('https://') else 'https://api.github.com'+path
    request=urllib.request.Request(url, data=body, method=method, headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','Content-Type':content_type,'X-GitHub-Api-Version':'2022-11-28','User-Agent':'SevaSaathi-release'})
    with urllib.request.urlopen(request, timeout=120) as response: return json.loads(response.read())

feed = ROOT/'docs/updates/stable.json'
previous = None
if feed.exists():
    env=json.loads(feed.read_text());payload=base64.b64decode(env['payload'], validate=True)
    public.verify(base64.b64decode(env['signature'], validate=True), payload, padding.PKCS1v15(), hashes.SHA256())
    previous=json.loads(payload)
sequence = 1 if MODE=='bootstrap' else (previous['sequence']+1 if previous else 1)
config = json.loads((ROOT/'release-config.json').read_text())
if set(config) != {'notes','events'}: raise SystemExit('Unexpected release-config fields.')
for language in ['en','hi']:
    notes=config['notes'][language]
    if not isinstance(notes,list) or not notes or len(notes)>20 or any(not isinstance(n,str) or len(n)>500 for n in notes): raise SystemExit('Invalid release notes.')
if not isinstance(config['events'],list) or len(config['events'])>100: raise SystemExit('Invalid events.')
for event in config['events']:
    for field,maximum in [('title',120),('venue',200),('description',2000),('titleHi',120),('descriptionHi',2000)]:
        v=event.get(field,'')
        if not isinstance(v,str) or len(v)>maximum: raise SystemExit('Invalid event field: '+field)
    if not event.get('title','').strip() or not event.get('venue','').strip(): raise SystemExit('Event name and venue are required.')
    datetime.date.fromisoformat(event['date'])
    if not re.fullmatch(r'\d{2}:\d{2}',event.get('time','')): raise SystemExit('A 24-hour event time is required.')
    hour,minute=map(int,event['time'].split(':'))
    if hour>23 or minute>59: raise SystemExit('Invalid event time.')
version=json.loads((ROOT/'version.json').read_text())
if MODE=='native':
    name=os.environ.get('NATIVE_VERSION','')
    code=int(os.environ.get('NATIVE_CODE','0'))
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?',name) or code<1: raise SystemExit('Set a valid native version and version code.')
    if previous and code<=previous['app']['versionCode']: raise SystemExit('Native versionCode must increase.')
    # An existing native release tag is never overwritten. Fix code and choose another version.
    build_env=dict(os.environ,VERSION_NAME=name,VERSION_CODE=str(code),UI_VERSION=str(sequence))
    subprocess.run(['python3',str(ROOT/'scripts/build_apk.py')],env=build_env,cwd=ROOT,check=True)
    version.update(versionName=name,versionCode=code,uiVersion=sequence)
elif MODE not in ['interface','bootstrap']:
    raise SystemExit('Unknown release kind.')
if MODE in ['native','bootstrap']:
    metadata=json.loads((ROOT/'artifacts/build-info.json').read_text())
    apkfile=ROOT/f'artifacts/Seva-Saathi-{metadata["versionName"]}.apk'
    apkbytes=apkfile.read_bytes()
    if sha(apkbytes)!=metadata['sha256']: raise SystemExit('APK changed after verification.')
    app={'versionCode':metadata['versionCode'],'versionName':metadata['versionName'],'minVersionCode':int(os.environ.get('MINIMUM_NATIVE',version.get('minVersionCode',1)) or 1),'apk':{'url':f'https://github.com/{OWNER}/{REPO}/releases/download/v{metadata["versionName"]}/Seva-Saathi.apk','bytes':len(apkbytes),'sha256':sha(apkbytes)}}
    app['apk']=onlyfiles.with_fallback(app['apk'],'Seva-Saathi-'+metadata['versionName']+'.apk',apkbytes)
    if not 1<=app['minVersionCode']<=app['versionCode']: raise SystemExit('Minimum native version exceeds the new APK.')
    if MODE=='native':
        # Upload fully before signing/publishing a feed that points to this artifact.
        release=api(f'/repos/{OWNER}/{REPO}/releases',{'tag_name':'v'+metadata['versionName'],'target_commitish':'main','name':'Seva Saathi '+metadata['versionName'],'body':'\n'.join('- '+n for n in config['notes']['en'])+'\n\nIndependent community app. Android 8.0+. SHA-256: `'+sha(apkbytes)+'`','draft':True},'POST')
        upload=release['upload_url'].split('{')[0]+'?name=Seva-Saathi.apk'
        api(upload,method='POST',raw=apkbytes,content_type='application/vnd.android.package-archive')
        api(f'/repos/{OWNER}/{REPO}/releases/{release["id"]}',{'draft':False},'PATCH')
else:
    if not previous: raise SystemExit('Publish the first native APK before an interface release.')
    app=previous['app']
    app['minVersionCode']=int(os.environ.get('MINIMUM_NATIVE',app['minVersionCode']) or app['minVersionCode'])
if not 1<=app['minVersionCode']<=app['versionCode']: raise SystemExit('Minimum native version exceeds the available APK.')
min_native=int(os.environ.get('UI_MIN_NATIVE','1') or 1)
if not 1<=min_native<=app['versionCode']: raise SystemExit('Invalid minimum native bridge version.')
files=[]; storage=ROOT/'docs/updates/files';storage.mkdir(parents=True,exist_ok=True)
for path in sorted((ROOT/'web').rglob('*')):
    if not path.is_file(): continue
    name=path.relative_to(ROOT/'web').as_posix()
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_./-]*',name) or '..' in name: raise SystemExit('Unsafe path.')
    data=path.read_bytes();digest=sha(data)
    if len(data)>10*1024*1024: raise SystemExit('Asset too large.')
    output=storage/digest
    if not output.exists(): output.write_bytes(data)
    meta={'path':name,'bytes':len(data),'sha256':digest,'url':f'https://raw.githubusercontent.com/{OWNER}/{REPO}/main/docs/updates/files/{digest}'}
    files.append(onlyfiles.with_fallback(meta,path.name,data))
if len(files)>256 or sum(f['bytes'] for f in files)>25*1024*1024: raise SystemExit('Bundle too large.')
published_at=os.environ.get('RELEASE_DATE') or datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds')
release={'schema':1,'channel':'stable','sequence':sequence,'publishedAt':published_at,'app':app,'ui':{'version':sequence,'minNative':min_native,'files':files},'notes':config['notes'],'events':config['events']}
payload=canonical(release)
signature=key.sign(payload,padding.PKCS1v15(),hashes.SHA256())
envelope={'algorithm':'SHA256withRSA','payload':base64.b64encode(payload).decode(),'signature':base64.b64encode(signature).decode()}
feed.write_bytes(canonical(envelope)+b'\n')
# Human-readable mirror is informational only. The Android app NEVER trusts this unsigned file.
(ROOT/'docs/updates/version.json').write_text(json.dumps(release,ensure_ascii=False,indent=2)+'\n')
version['minVersionCode']=app['minVersionCode']
if MODE=='native': (ROOT/'version.json').write_text(json.dumps(version,indent=2)+'\n')
import shutil
preview=ROOT/'docs/app'
if preview.exists():shutil.rmtree(preview)
shutil.copytree(ROOT/'web',preview)
print(f'Signed release sequence {sequence}; UI {sequence}; native {app["versionName"]}; {len(files)} content-addressed files.')
