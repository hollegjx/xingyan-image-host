#!/usr/bin/env python3
"""Explicit live release gate: creates one isolated image plus two derivatives, then removes them.

Requires requests and a previously authorized private test bucket. Credentials stay in memory.
Never use this against a public bucket or without permission to create disposable test objects.
"""
from pathlib import Path
import argparse, hashlib, hmac, time, uuid, json, os, re, sys, urllib.parse
import xml.etree.ElementTree as ET
import requests

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--run-live', action='store_true', required=True)
parser.add_argument('--properties', type=Path, required=True)
parser.add_argument('--private-bucket', required=True)
parser.add_argument('--fixture', type=Path, required=True)
parser.add_argument('--explicit-original-acl', action='store_true')
parser.add_argument('--journal', type=Path, required=True)
args = parser.parse_args()
if not __debug__:
    raise SystemExit("Run without Python optimization so all release assertions remain active")
assert args.properties.stat().st_mode & 0o077 == 0, 'credential file must be private'
current = dict(line.strip().split('=', 1) for line in args.properties.read_text().splitlines()
               if '=' in line and not line.lstrip().startswith('#'))
sid = current['cos.client.secretId']; skey = current['cos.client.secretKey']
PRIVATE = args.private_bucket
assert re.fullmatch(r'[a-z0-9-]+-[0-9]+', PRIVATE)
assert PRIVATE != current['cos.client.bucket']
assert args.fixture.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
session = requests.Session(); session.trust_env = False

def esc(s):return urllib.parse.quote(str(s),safe='-_.~')
def request(method,bucket,key,params=None,headers=None,body=None,signed=True):
 host=f'{bucket}.cos.accelerate.myqcloud.com';path='/'+key
 params=params or {};headers={k.lower():v for k,v in (headers or {}).items()};headers['host']=host
 if signed:
  stamp=int(time.time());kt=f'{stamp-60};{stamp+600}'
  hp='&'.join(esc(k.lower())+'='+esc(v) for k,v in sorted(params.items(),key=lambda x:x[0].lower()))
  hh='&'.join(esc(k)+'='+esc(v) for k,v in sorted(headers.items()))
  http_string=method.lower()+'\n'+path+'\n'+hp+'\n'+hh+'\n'
  sign_key=hmac.new(skey.encode(),kt.encode(),hashlib.sha1).hexdigest()
  string_to_sign='sha1\n'+kt+'\n'+hashlib.sha1(http_string.encode()).hexdigest()+'\n'
  sig=hmac.new(sign_key.encode(),string_to_sign.encode(),hashlib.sha1).hexdigest()
  headers['authorization']='q-sign-algorithm=sha1&q-ak='+sid+'&q-sign-time='+kt+'&q-key-time='+kt+'&q-header-list='+';'.join(sorted(headers))+'&q-url-param-list='+';'.join(sorted(k.lower() for k in params))+'&q-signature='+sig
 return session.request(method,'https://'+host+path,params=params,headers=headers,data=body,timeout=(10,30),allow_redirects=False)
base='space/permission-check/m22-'+uuid.uuid4().hex
keys=[base+'.png',base+'_display.webp',base+'_thumbnail.webp']
journal=args.journal
assert not journal.exists()
fd=os.open(journal,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:json.dump(keys,f)
checks=[];failed=False
def call(label,method,key,**kwargs):
    r=request(method,PRIVATE,key,**kwargs)
    checks.append({'operation':label,'http':r.status_code})
    return r
try:
    r=call('bucket_acl','GET','',params={'acl':''});assert r.status_code==200
    acl=ET.fromstring(r.content);owner=acl.findtext('./Owner/ID');grants=acl.findall('.//Grant')
    assert owner and grants and all(g.findtext('./Grantee/ID')==owner and g.find('./Grantee/URI') is None for g in grants)
    r=call('bucket_policy','GET','',params={'policy':''})
    if r.status_code==404:
        # COS returns an empty 404 for an unconfigured policy; bucket existence was checked above.
        assert not r.content and r.headers.get('Server','').lower()=='tencent-cos' and r.headers.get('x-cos-request-id')
        checks.append({'operation':'bucket_policy_absent','confirmed':True})
    else:
        assert r.status_code==200
        policy=r.json();statements=policy.get('Statement',policy.get('statement'))
        assert isinstance(statements,list)
        def public_principal(value):
            if isinstance(value,dict):return not value or any(public_principal(v) for v in value.values())
            if isinstance(value,list):return not value or any(public_principal(v) for v in value)
            return value is None or '*' in str(value) or 'anyone' in str(value).lower()
        for statement in statements:
            effect=str(statement.get('Effect',statement.get('effect',''))).lower()
            assert effect in ('allow','deny')
            if effect=='allow':assert not public_principal(statement.get('Principal',statement.get('principal')))
        checks.append({'operation':'bucket_policy_public_allow','public':False})
    ops={'is_pic_info':1,'rules':[
       {'bucket':PRIVATE,'fileid':'/'+keys[1],'rule':'imageMogr2/format/webp/quality/85'},
       {'bucket':PRIVATE,'fileid':'/'+keys[2],'rule':'imageMogr2/thumbnail/512x512>/format/webp/quality/80'}]}
    headers={'content-type':'image/png','pic-operations':json.dumps(ops,separators=(',',':'))}
    if args.explicit_original_acl:headers['x-cos-acl']='private'
    r=call('put_without_derivative_acl','PUT',keys[0],headers=headers,body=args.fixture.read_bytes())
    assert r.status_code==200
    root=ET.fromstring(r.content)
    returned={e.text.lstrip('/') for e in root.findall('.//ProcessResults/Object/Key') if e.text}
    checks.append({'operation':'both_derivatives_returned','confirmed':returned==set(keys[1:])})
    assert returned==set(keys[1:])
    for label,key in zip(['original','display','thumbnail'],keys):
        r=call(label+'_signed_get','GET',key);assert r.status_code==200 and r.content
        r=call(label+'_anonymous_get','GET',key,signed=False);assert r.status_code==403
        r=call(label+'_acl','GET',key,params={'acl':''});assert r.status_code==200
        acl=ET.fromstring(r.content);owner=acl.findtext('./Owner/ID')
        public=any(g.find('./Grantee/URI') is not None or g.findtext('./Grantee/ID') not in (None,owner) for g in acl.findall('.//Grant'))
        checks.append({'operation':label+'_acl_public','public':public});assert not public
except Exception as error:
    checks.append({'operation':'probe_failed','error_type':type(error).__name__});failed=True
finally:
    remaining=[]
    for label,key in zip(['original','display','thumbnail'],keys):
        try:
            r=call(label+'_delete_exact','DELETE',key);assert r.status_code==204
            r=call(label+'_signed_absence','GET',key);assert r.status_code==404
        except Exception:remaining.append(key)
    if remaining:
        journal.write_text(json.dumps(remaining));failed=True
    else:journal.unlink()
    checks.append({'operation':'all_probe_objects_removed','confirmed':not remaining})
print(json.dumps({'checks':checks,'passed':not failed}))
sys.exit(2 if failed else 0)
