#!/usr/bin/env python3
"""Capture public, unauthenticated API data through normal network policy. Never fabricate a fixture."""
import datetime
import hashlib
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request

root = pathlib.Path(__file__).resolve().parents[1]
output = root / 'app/src/test/resources/live'
base = 'https://www.sakugabooru.com/'

def fetch(path):
    request = urllib.request.Request(base + path, headers={
        'Accept': 'application/json',
        'User-Agent': 'Sakugapp-research/0.2 (+https://github.com/Milenal82/sakugapp)',
    })
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read(8 * 1024 * 1024 + 1)
        if len(raw) > 8 * 1024 * 1024:
            raise ValueError('Response exceeds size bound')
        return json.loads(raw), hashlib.sha256(raw).hexdigest()

try:
    posts, post_hash = fetch('post.json?limit=2&page=1&tags=rating%3Asafe')
    summary, summary_hash = fetch('tag/summary.json')
    if not isinstance(posts, list) or not posts or not isinstance(summary.get('data'), str):
        raise ValueError('Unexpected public API shape; no fixture written')
    # Exclude author, user IDs, source text, cookies, response headers, etc.
    keep = {'id', 'tags', 'score', 'preview_url', 'preview_width', 'preview_height',
            'file_url', 'file_ext', 'width', 'height', 'rating', 'created_at', 'status'}
    cleaned = []
    names = set()
    for post in posts:
        if not isinstance(post.get('id'), int) or post['id'] <= 0:
            raise ValueError('Invalid post ID; no fixture written')
        row = {key: value for key, value in post.items() if key in keep}
        names.update(row.get('tags', '').split())
        for key in ['preview_url', 'file_url']:
            if isinstance(row.get(key), str):
                uri = urllib.parse.urlsplit(row[key])
                if uri.scheme != 'https' or uri.username or uri.password:
                    row.pop(key)
                else:
                    row[key] = urllib.parse.urlunsplit((uri.scheme, uri.netloc, uri.path, '', ''))
        cleaned.append(row)
    # Keep only classification records corresponding to the sampled posts.
    records = [record for record in summary['data'].split()
               if any(part in names for part in record.split('`')[1:])]
    reduced = {'version': summary.get('version'), 'data': ' '.join(records) + ' '}
    provenance = {
        'retrieved_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'post_url': base + 'post.json?limit=2&page=1&tags=rating%3Asafe',
        'summary_url': base + 'tag/summary.json',
        'raw_sha256': {'posts': post_hash, 'tag_summary': summary_hash},
        'sanitization': 'Whitelisted post fields; URL query/fragment removed; summary reduced to sampled tags; no credentials or response headers.',
    }
    output.mkdir(parents=True, exist_ok=True)
    for filename, value in [('posts.json', cleaned), ('tags.json', reduced), ('provenance.json', provenance)]:
        (output / filename).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    print('Captured public fixture; review provenance and content before committing:', output)
except (urllib.error.URLError, ValueError, KeyError, TypeError) as error:
    raise SystemExit(f'Public fixture capture failed; no fabricated data written: {error}')
