"""OnlyFiles public release-file adapter, with byte-for-byte verification.
Never call this for journals, backups, tokens, or signing keys.
The API returns a HTML landing page, not a stable raw file URL. The app resolves
its fresh download link natively on every use, then checks the signed SHA-256.
GitHub remains an independently available fallback for every file.
"""
from pathlib import Path
import hashlib, html, json, mimetypes, re
from urllib.parse import urlparse
import requests

class OnlyFiles:
    def __init__(self, root: Path):
        self.path=root/'docs/updates/onlyfiles-index.json'
        self.path.parent.mkdir(parents=True,exist_ok=True)
        self.index=json.loads(self.path.read_text()) if self.path.exists() else {}
    @staticmethod
    def get(url, maximum):
        for _ in range(5):
            parsed=urlparse(url)
            if parsed.scheme!='https' or parsed.hostname!='onlyfiles.com' or parsed.username or parsed.port not in [None,443]:
                raise ValueError('Unexpected OnlyFiles URL')
            with requests.get(url,stream=True,timeout=(15,30),allow_redirects=False) as response:
                if response.status_code in [301,302,303,307,308]:
                    from urllib.parse import urljoin
                    url=urljoin(url,response.headers['Location']);continue
                response.raise_for_status();data=bytearray()
                for chunk in response.iter_content(32768):
                    data.extend(chunk)
                    if len(data)>maximum:raise ValueError('OnlyFiles response exceeds size bound')
                return bytes(data)
        raise ValueError('Redirect limit')
    @classmethod
    def binary(cls, page_url, maximum):
        page=cls.get(page_url,128*1024).decode('utf-8')
        match=re.search(r'<a[^>]*class="download"[^>]*href="(https://onlyfiles\.com/dl/[^\"]+)"',page,re.I)
        if not match:raise ValueError('OnlyFiles direct download link unavailable')
        return cls.get(html.unescape(match.group(1)),maximum)
    def upload_verified(self,name,data):
        digest=hashlib.sha256(data).hexdigest()
        if digest in self.index:
            entry=self.index[digest]
            if urlparse(entry.get('url','')).hostname=='onlyfiles.com' and entry.get('bytes')==len(data):
                return entry['url']
        response=requests.post('https://api.onlyfiles.com/v1/upload',files={'file':(name,data,mimetypes.guess_type(name)[0] or 'application/octet-stream')},data={'expire':'0'},timeout=(15,60))
        response.raise_for_status();result=response.json()
        if result.get('status') is not True:raise ValueError('OnlyFiles rejected upload: '+result.get('error',{}).get('type','unknown'))
        url=result['data']['file']['url']['full']
        verified=self.binary(url,len(data))
        if verified!=data:raise ValueError('OnlyFiles binary bytes do not match source')
        self.index[digest]={'url':url,'bytes':len(data),'name':name}
        self.path.write_text(json.dumps(self.index,indent=2)+'\n')
        return url
    def with_fallback(self,metadata,name,data):
        try:
            page=self.upload_verified(name,data)
            return dict(metadata,url=page,mirrors=[metadata['url']])
        except Exception as error:
            # An unreliable mirror must never prevent a safe GitHub-backed release.
            print('OnlyFiles unavailable for '+name+'; retaining GitHub source ('+type(error).__name__+').')
            return metadata
