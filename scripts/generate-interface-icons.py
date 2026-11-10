#!/usr/bin/env python3
"""Original miniature keyboard SVGs; module icons use vanilla Minecraft PNGs."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'macos/ui/assets/designed'
OUT.mkdir(exist_ok=True)
profiles=re.findall(r"\['([^']+)','([^']+)'\]",(ROOT/'macos/ui/customization.js').read_text().split('let streamStep')[0])
colors=['#fa7373','#72b4ff','#c9956b','#777e96','#b68aff','#89d2a1','#f4dfb2','#f0f0ec','#bca3ff','#8f96b5','#91c6ff','#d5aa7f','#ff9898','#f5ddb5','#70e0d0','#e8b6d5','#8ebed8','#79778c','#ce707c','#72a8c5']
for i,(name,_) in enumerate(profiles):
 c=colors[i];keys=''
 for r in range(4):
  for k in range(10+(i%3==0)):
   x=8+k*8.1+(r%2)*1.4;y=8+r*7
   fill=c if (r+k+i)%5==0 else ['#252636','#e3ddce','#3e3546'][i%3]
   keys+=f'<rect x="{x}" y="{y}" width="6.3" height="5" rx="1" fill="{fill}" stroke="{c}" stroke-opacity=".35" stroke-width=".5"/>'
 keys+=f'<rect x="30" y="36" width="42" height="5" rx="1.5" fill="{c}"/>'
 (OUT/('keyboard-'+name+'.svg')).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="112" height="52" viewBox="0 0 112 52"><rect x="2" y="3" width="106" height="46" rx="7" fill="#10101a" stroke="{c}" stroke-width="2"/><path d="M4 43H106" stroke="{c}" stroke-opacity=".5"/>{keys}<circle cx="102" cy="38" r="2" fill="{c}"/></svg>')
(OUT/'LICENSE.txt').write_text('Original Tung Tung Client keyboard illustrations. MIT, see project LICENSE.\n')
print(f'Generated {len(profiles)} distinct miniature keyboards')
