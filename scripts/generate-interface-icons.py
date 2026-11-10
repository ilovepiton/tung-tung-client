#!/usr/bin/env python3
"""Original SVG module symbols and miniature keyboards; no external image inputs."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'macos/ui/assets/designed'
OUT.mkdir(exist_ok=True)
shapes={
'client_maps':'M6 7 16 4 26 7 26 25 16 28 6 25Z M16 4V28 M6 16 16 13 26 16',
'map_in_slot':'M4 4H28V28H4Z M8 8H24V24H8Z M9 20 14 14 18 18 22 11',
'invis_mode':'M5 16Q16 2 27 16Q16 30 5 16 M3 29 29 3 M12 13 20 19',
'item_finder':'M5 10H22V27H5Z M5 10 10 5H27V21L22 27 M22 10 27 5 M23 23 29 29',
'night_vision':'M22 4A12 12 0 1 0 28 23A12 12 0 0 1 22 4 M24 4V10 M21 7H27',
'elytra_finder':'M15 8 6 4 3 18 12 26 15 17 M17 8 26 4 29 18 20 26 17 17 M16 6V22',
'auto_vault':'M6 5H26V28H6Z M11 10H21V22H11Z M16 12V18 M14 16H18 M3 3 8 8',
'mace_bot':'M5 5 14 3 19 9 15 16 7 15 3 10Z M14 16 25 28 M20 21 24 18',
'combat_bot':'M6 9H26V26H6Z M16 3V9 M12 15V18 M20 15V18 M11 22H21 M3 13V22 M29 13V22',
'diamond_esp':'M4 11 10 4H22L28 11 16 28Z M4 11H28 M10 4 16 28 22 4',
'ore_esp':'M5 7H27V27H5Z M9 13 13 9 17 13 13 17Z M18 22 22 18 25 22 22 25Z',
'lava_esp':'M9 5H23L26 25Q16 30 6 25Z M9 5Q16 1 23 5 M9 19Q12 14 16 19Q20 23 24 18',
'chunk_finder':'M4 8 16 3 28 8V24L16 29 4 24Z M4 8 16 13 28 8 M16 13V29 M10 6V26 M22 6V26 M4 16 16 21 28 16',
'storage_esp':'M4 10H28V26H4Z M4 16H28 M14 13H18V20H14Z M7 10V6H25V10',
'amethyst_esp':'M12 4 6 13 12 28 19 13Z M19 7 25 11 28 19 20 26 M12 4V28',
'xray':'M5 5H27V27H5Z M8 23 13 16 18 21 24 9 M8 9H12 M20 25H25',
'player_esp':'M16 3 27 27 16 21 5 27Z M16 21V10 M3 5H8 M24 5H29',
'freelook':'M3 16Q16 3 29 16Q16 29 3 16 M12 16A4 4 0 1 0 20 16A4 4 0 1 0 12 16 M4 5H11 M4 5V12',
'freecam':'M5 10H11L14 6H23L26 10H29V26H3V10Z M12 18A5 5 0 1 0 22 18A5 5 0 1 0 12 18 M2 3H8 M2 3V8',
'base_chunks':'M4 15 16 4 28 15 M7 13V28H25V13 M13 28V20H19V28',
'player_chunks':'M4 4H28V28H4Z M4 16H28 M16 4V28 M11 12A5 5 0 1 0 21 12A5 5 0 1 0 11 12 M10 25Q16 18 22 25',
'spawner_chunks':'M5 8 16 3 27 8V24L16 29 5 24Z M5 8 16 13 27 8 M16 13V29 M10 10V25 M22 10V25 M11 18H21',
'player_detection':'M13 9A4 4 0 1 0 21 9A4 4 0 1 0 13 9 M8 26Q16 12 25 26 M3 8V3H9 M24 3H29V8',
'auto_totem':'M11 4H21V12H11Z M6 14H26V20H21V28H11V20H6Z M14 8H15 M18 8H19',
'killaura':'M7 24 25 6 28 3 29 8 11 27 M4 20 15 31 M4 29 8 25 M4 7Q13 1 22 5',
'auto_crystal':'M16 3 26 13 16 26 6 13Z M6 13H26 M16 3 12 13 16 26 20 13Z M6 29H26',
'auto_anchor':'M16 3V26 M11 7H21 M6 15H26 M4 18Q5 28 16 28Q27 28 28 18 M4 18 2 23 M28 18 30 23',
'auto_gapple':'M16 9Q5 4 5 17Q5 30 16 27Q27 30 27 17Q27 4 16 9 M16 9V4 M17 7Q19 1 25 4Q23 9 17 7',
 'trigger_bot':'M4 16H10 M22 16H28 M16 4V10 M16 22V28 M10 10H22V22H10Z M14 16H18',
'auto_weapon':'M5 5 15 15 M3 9 9 3 M14 16 25 27 M20 27 27 20 M24 3 29 8 18 19 13 14Z',
'aim_cursor':'M5 4V27L12 20 18 28 22 25 17 17H27Z M23 4V10 M20 7H26',
'auto_punch':'M5 14V24L10 28H23L28 22V13H23V7H18V5H13V7H8V15 M13 7V15 M18 7V15 M23 13V18',
'auto_kill_steal':'M3 9H12L16 5 20 9H29L16 27Z M12 16 16 20 22 12 M5 3V7 M3 5H7',
'player_info':'M8 8A8 8 0 1 0 24 8A8 8 0 1 0 8 8 M4 29Q16 12 28 29 M16 5V6 M16 9V13',
'flight':'M3 19 29 4 21 29 15 19Z M15 19 29 4 M4 27H10',
'noclip':'M13 3H23V29H13 M3 16H27 M22 11 27 16 22 21 M8 4V8 M8 24V28',
'boat_fly':'M3 20H29L24 28H9Z M9 20V16H23V20 M16 3V14 M11 8 16 3 21 8',
'fast_swim':'M3 19Q7 15 11 19Q15 23 19 19Q23 15 29 19 M3 26Q7 22 11 26Q15 30 19 26Q23 22 29 26 M7 9H22 M18 5 23 9 18 13',
'super_trident':'M16 4V29 M7 4V13Q16 20 25 13V4 M3 8 7 4 11 8 M12 8 16 4 20 8 M21 8 25 4 29 8',
'deepslate_bypass':'M4 4H28V28H4Z M4 12H28 M4 20H28 M12 4V12 M20 12V20 M12 20V28 M6 27 27 6',
'donut_flight_bypass':'M4 16A12 12 0 1 0 28 16A12 12 0 1 0 4 16 M12 16A4 4 0 1 0 20 16A4 4 0 1 0 12 16 M9 7 11 10 M23 9 21 12 M24 22 21 21',
'anti_kick':'M16 3 27 7V16Q26 24 16 29Q6 24 5 16V7Z M10 16 14 20 23 11',
'auction_sniper':'M7 3 17 13 12 18 2 8Z M14 15 27 28 M3 27H16 M4 23H15 M23 3V9 M20 6H26',
}
for i,(name,path) in enumerate(shapes.items()):
 color=['#ffc46b','#69e0cf','#b49cff','#ff8db2','#8cc8ff'][i%5]
 svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 40 40"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="{color}" stop-opacity=".25"/><stop offset="1" stop-color="#171018"/></linearGradient></defs><rect x="1" y="1" width="38" height="38" rx="10" fill="url(#g)" stroke="{color}" stroke-opacity=".35"/><g transform="translate(4 4)" fill="none" stroke="{color}" stroke-width="1.7" stroke-linejoin="round" stroke-linecap="round"><path d="{path}"/></g></svg>'
 (OUT/(name+'.svg')).write_text(svg)
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
(OUT/'LICENSE.txt').write_text('Original Tung Tung Client interface symbols and keyboard illustrations. MIT, see project LICENSE.\n')
print(f'Generated {len(shapes)} module symbols and {len(profiles)} distinct miniature keyboards')
