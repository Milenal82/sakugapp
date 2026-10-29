#!/usr/bin/env python3
"""Resource/argument/plural coverage; this is not native-speaker linguistic review."""
import pathlib,re,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
folders=['values-zh-rCN','values-zh-rTW','values-ja','values-fr','values-es','values-ru','values-ko']
def resources(folder):
    nodes=[n for path in (res/folder).glob('*.xml') for n in ET.parse(path).getroot() if n.tag in {'string','plurals'}]
    names=[n.attrib['name'] for n in nodes]
    assert len(names)==len(set(names)),f'{folder}: duplicate resources'
    return {n.attrib['name']:n for n in nodes}
base=resources('values')
pattern=re.compile(r'%(\d+)\$([sdf])')
required={'values-fr':{'one','many','other'},'values-es':{'one','many','other'},'values-ru':{'one','few','many','other'}}
for folder in folders:
    translated=resources(folder)
    for name,node in base.items():
        if node.get('translatable')=='false':continue
        assert name in translated,f'{folder}: missing {name}'
        local=translated[name];assert node.tag==local.tag,f'{folder}: wrong resource type for {name}'
        targets=list(local) if local.tag=='plurals' else [local]
        original=''.join(node.find("item[@quantity='other']").itertext()) if node.tag=='plurals' else ''.join(node.itertext())
        for target in targets:
            text=''.join(target.itertext())
            assert text.strip(),f'{folder}: empty {name}'
            assert sorted(pattern.findall(original))==sorted(pattern.findall(text)),f'{folder}: argument mismatch in {name}'
        if node.tag=='plurals':assert required.get(folder,{'other'})<={n.get('quantity') for n in local},f'{folder}: plural forms missing in {name}'
refs=set()
for path in (root/'app/src/main/java').rglob('*.kt'):
    source=path.read_text();refs.update(re.findall(r'R\.(?:string|plurals)\.([a-zA-Z0-9_]+)',source))
    assert not re.search(r'\bText\("[A-Za-z]',source),f'Hardcoded UI text in {path.relative_to(root)}'
assert refs<=base.keys(),f'Missing referenced resources: {refs-base.keys()}'
print(f'Localization verified: {len(base)} resources, all 8 UI languages; placeholders and locale plural categories checked. No native-speaker review claimed.')
