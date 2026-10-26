#!/usr/bin/env python3
"""Validate Android resource coverage and positional formatting, without translating site content."""
import pathlib,re,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
def strings(folder):
    return {node.attrib['name']:node for node in ET.parse(res/folder/'strings.xml').getroot().findall('string')}
base=strings('values')
pattern=re.compile(r'%(\d+)\$([sdf])')
for folder in ['values-zh-rCN','values-zh-rTW','values-ja']:
    translated=strings(folder)
    for name,node in base.items():
        if node.get('translatable')=='false':continue
        assert name in translated and ''.join(translated[name].itertext()).strip(),f'{folder}: missing {name}'
        original=''.join(node.itertext());local=''.join(translated[name].itertext())
        assert sorted(pattern.findall(original))==sorted(pattern.findall(local)),f'{folder}: argument mismatch in {name}'
refs=set()
for path in (root/'app/src/main/java').rglob('*.kt'):
    source=path.read_text()
    refs.update(re.findall(r'R\.string\.([a-zA-Z0-9_]+)',source))
    assert not re.search(r'\bText\("[A-Za-z]',source),f'Hardcoded UI text in {path.relative_to(root)}'
assert refs<=base.keys(),f'Missing referenced strings: {refs-base.keys()}'
print(f'Localization verified: {len(base)} base strings, complete zh-CN/zh-TW/ja coverage and matching format arguments.')
