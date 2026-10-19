#!/usr/bin/env python3
"""One-time repo setup: Pages + encrypted Actions secrets. Never prints secrets."""
from pathlib import Path
import json, urllib.request, urllib.error, base64, os
from nacl.public import SealedBox, PublicKey

TOKEN = os.environ['GH_TOKEN']
OWNER, REPO = 'glitchyapi', 'seva-saathi-pro'

def api(path, data=None, method=None, raw=None, content_type=None):
    headers = {'Authorization': 'Bearer ' + TOKEN, 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'SevaSaathi-setup'}
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    if body is not None:
        headers['Content-Type'] = content_type or 'application/json'
    req = urllib.request.Request('https://api.github.com' + path, data=body, headers=headers,
                                 method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()

if __name__ == '__main__':
    st, body = api(f'/repos/{OWNER}/{REPO}/pages', {'source': {'branch': 'main', 'path': '/docs'}}, 'POST')
    print('pages create:', st, body[:180].decode(errors='replace'))
    if st in (400, 409, 422):
        st, body = api(f'/repos/{OWNER}/{REPO}/pages', {'source': {'branch': 'main', 'path': '/docs'}}, 'PUT')
        print('pages update:', st, body[:180].decode(errors='replace'))
    st, body = api(f'/repos/{OWNER}/{REPO}/actions/secrets/public-key')
    pk = json.loads(body)
    print('secret key id:', pk.get('key_id', 'MISSING'))
    box = SealedBox(PublicKey(base64.b64decode(pk['key'])))
    for name, value in [
        ('UPDATE_PRIVATE_KEY', Path('/home/user/seva-keys/update-private.pem').read_text()),
        ('APK_KEYSTORE_B64', base64.b64encode(Path('/home/user/seva-keys/seva-release.jks').read_bytes()).decode()),
        ('APK_STORE_PASSWORD', Path('/home/user/seva-keys/store-password.txt').read_text().strip()),
    ]:
        enc = box.encrypt(value.encode())
        st, body = api(f'/repos/{OWNER}/{REPO}/actions/secrets/{name}',
                       {'encrypted_value': base64.b64encode(enc).decode(), 'key_id': pk['key_id']}, 'PUT')
        print('secret', name, st, body[:120].decode(errors='replace'))
    st, body = api(f'/repos/{OWNER}/{REPO}/actions/workflows')
    print('workflows:', st, body[:250].decode(errors='replace'))