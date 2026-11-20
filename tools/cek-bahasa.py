#!/usr/bin/env python3
"""Gagalkan binaan jika ada teks Melayu dalam kod/nota tanpa terjemahan English.
Teks dengan $ (dinamik) dan istilah yang sengaja kekal sama disenaraikan dalam tools/bahasa-kecuali.txt."""
import re, json, glob, sys, os
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
d = json.load(open(f'{R}/app/src/main/assets/i18n.json'))
en = d['en']
kecuali = set()
p = f'{R}/tools/bahasa-kecuali.txt'
if os.path.exists(p):
    kecuali = {l.rstrip('\n') for l in open(p, encoding='utf-8') if l.strip() and not l.startswith('#')}
calls = r'(?:Text|Judul|Baris|Etiket|toast|Bhs\.t|Bhs\.f|lapor|label|title|placeholder|KadWr|Catatan|Butang|Suis|Pilih|Chip|Btn|Cip)'
lit = re.compile(r'"((?:[^"\\]|\\.)*)"')
miss = {}
for f in glob.glob(f'{R}/app/src/main/java/com/vlesspro/pemasang/*.kt'):
    if f.endswith('Bhs.kt'): continue
    for n, l in enumerate(open(f, encoding='utf-8').read().split('\n'), 1):
        if not re.search(calls, l): continue
        for m in lit.finditer(l):
            s = m.group(1)
            if not re.search(r'[A-Za-z]{3}', s): continue
            if re.match(r'^[a-z_/\.:\-A-Z0-9]+$', s) and ' ' not in s: continue
            if s.startswith('http') or s.startswith('/api') or '$' in s: continue
            if s not in en and s not in kecuali:
                miss.setdefault(s, []).append(f'{os.path.basename(f)}:{n}')
c = json.load(open(f'{R}/catatan-apl.json'))
for s in [c['title']] + c['items']:
    if s not in en: miss.setdefault(s, []).append('catatan-apl.json')
if miss:
    print(f'GAGAL: {len(miss)} teks tiada terjemahan English dalam i18n.json:')
    for s, w in sorted(miss.items(), key=lambda x: x[1][0]): print(f'  {w[0]} | {s}')
    print('Tambah dalam app/src/main/assets/i18n.json (bahagian "en"), atau senaraikan dalam tools/bahasa-kecuali.txt jika sengaja kekal sama.')
    sys.exit(1)
print('Semakan bahasa: semua teks ada terjemahan English.')
