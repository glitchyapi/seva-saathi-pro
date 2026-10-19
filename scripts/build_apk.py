#!/usr/bin/env python3
"""Build a real release APK using official SDK tools, without Gradle dependencies.
Required environment: ANDROID_HOME, APK_KEYSTORE, APK_STORE_PASSWORD.
The matching signing key must be reused for every future native update.
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', '/home/user/.cache/android-sdk'))
PLATFORM = SDK / 'platforms/android-34/android.jar'
SDK_TOOLS = sorted((SDK / 'build-tools').glob('*'), reverse=True)
TOOLS = next((p for p in SDK_TOOLS if (p / 'aapt2').exists()), None)
if TOOLS is None or not PLATFORM.exists():
    raise SystemExit('Install platforms;android-34 and build-tools;34.0.0 (or newer) into ANDROID_HOME.')
version = json.loads((ROOT / 'version.json').read_text())
code = int(os.environ.get('VERSION_CODE', version['versionCode']))
name = os.environ.get('VERSION_NAME', version['versionName'])
ui = int(os.environ.get('UI_VERSION', version['uiVersion']))
if not __import__('re').fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?', name) or code < 1 or ui < 1:
    raise SystemExit('Invalid version.')
work = ROOT / 'build/apk'
if work.exists(): shutil.rmtree(work)
work.mkdir(parents=True)
for d in ['classes', 'gen/org/sevasaathi/app', 'dex', 'assets']: (work / d).mkdir(parents=True, exist_ok=True)
shutil.copytree(ROOT / 'web', work / 'assets/web')
shutil.copy(ROOT / 'app/src/main/assets/update-public.der', work / 'assets/update-public.der')
(work / 'gen/org/sevasaathi/app/BuildConfig.java').write_text(f'package org.sevasaathi.app; public final class BuildConfig {{public static final int VERSION_CODE={code}; public static final String VERSION_NAME="{name}"; public static final int UI_VERSION={ui};}}')

def run(cmd):
    # Never log credentials or signing password arguments.
    subprocess.run([str(x) for x in cmd], check=True, cwd=ROOT)

run([TOOLS/'aapt2', 'compile', '--dir', ROOT/'app/src/main/res', '-o', work/'resources.zip'])
run([TOOLS/'aapt2', 'link', '-o', work/'unsigned.apk', '-I', PLATFORM, '--manifest', ROOT/'app/src/main/AndroidManifest.xml', '--java', work/'gen', '--min-sdk-version', '26', '--target-sdk-version', '34', '--version-code', str(code), '--version-name', name, '-A', work/'assets', work/'resources.zip', '--auto-add-overlay'])
sources = list((ROOT/'app/src/main/java').rglob('*.java')) + list((work/'gen').rglob('*.java'))
run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', PLATFORM, '-d', work/'classes', *sources])
run(['jar', 'cf', work/'classes.jar', '-C', work/'classes', '.'])
run([TOOLS/'d8', '--lib', PLATFORM, '--min-api', '26', '--output', work/'dex', work/'classes.jar'])
with zipfile.ZipFile(work/'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as z:
    for f in (work/'dex').glob('*.dex'): z.write(f, f.name)
run([TOOLS/'zipalign', '-p', '-f', '4', work/'unsigned.apk', work/'aligned.apk'])
key = Path(os.environ.get('APK_KEYSTORE', '/home/user/.seva-keys/release.jks'))
if not os.environ.get('APK_STORE_PASSWORD'):
    fallback = Path('/home/user/.seva-keys/store-password.txt')
    if fallback.exists(): os.environ['APK_STORE_PASSWORD'] = fallback.read_text().strip()
if not key.exists() or not os.environ.get('APK_STORE_PASSWORD'): raise SystemExit('Release signing key is required. No unsigned or debug APK will be emitted.')
outdir = ROOT/'artifacts'; outdir.mkdir(exist_ok=True)
output = outdir / f'Seva-Saathi-{name}.apk'
run([TOOLS/'apksigner', 'sign', '--ks', key, '--ks-key-alias', os.environ.get('APK_KEY_ALIAS','seva-release'), '--ks-pass', 'env:APK_STORE_PASSWORD', '--key-pass', 'env:APK_STORE_PASSWORD', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', output, work/'aligned.apk'])
run([TOOLS/'apksigner', 'verify', '--verbose', '--print-certs', output])
run([TOOLS/'zipalign', '-c', '-v', '4', output])
import hashlib
sha = hashlib.sha256(output.read_bytes()).hexdigest()
(output.with_suffix('.apk.sha256')).write_text(f'{sha}  {output.name}\n')
(outdir/'build-info.json').write_text(json.dumps({'package':'org.sevasaathi.app','versionCode':code,'versionName':name,'uiVersion':ui,'minSdk':26,'targetSdk':35,'bytes':output.stat().st_size,'sha256':sha}, indent=2)+'\n')
print(f'\nVERIFIED APK: {output}\nBytes: {output.stat().st_size}\nSHA-256: {sha}')
