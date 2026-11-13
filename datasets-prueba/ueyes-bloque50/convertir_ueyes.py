"""Convierte logs Gazepoint de UEyes (fijaciones, coords. de pantalla 1920x1200) al formato generico de UX-BioDash."""
import csv, glob, os, re, shutil, sys
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
SW, SH = 1920, 1200
imgs = {'de022a.png': 'web', '3d29ac.jpg': 'mobile', '8ed8ac.png': 'desktop', '4d0fc2.jpg': 'poster'}
for img, cat in imgs.items():
    w, h = Image.open(os.path.join(src, img)).size
    # misma transformacion que generate_heatmaps.py de los autores (imagen centrada con relleno)
    if SW / SH >= w / h: sx, sy = h / SH * SW, h
    else: sx, sy = w, w / SW * SH
    px, py = (sx - w) / 2, (sy - h) / 2
    filas, fuera = [], 0
    for f in sorted(glob.glob(os.path.join(src, '50_*_fixations.csv'))):
        pid = 'P' + re.search(r'_[kK][hH](\d+)_', f).group(1)
        vistos = {}
        for r in csv.DictReader(open(f, encoding='utf-8', errors='replace')):
            if r['MEDIA_NAME'] != img or r['FPOGV'] != '1': continue
            d = float(r['FPOGD'])
            if d <= 0: continue
            x = float(r['FPOGX']) * sx - px; y = float(r['FPOGY']) * sy - py
            if not (0 <= x < w and 0 <= y < h): fuera += 1; continue
            vistos[r['FPOGID']] = (pid, round(x, 1), round(y, 1), round(float(r['FPOGS']) * 1000), round(d * 1000))
        filas += sorted(vistos.values(), key=lambda t: t[3])
    base = f'{cat}_{os.path.splitext(img)[0]}'
    with open(os.path.join(out, base + '_fijaciones.csv'), 'w', newline='', encoding='utf-8') as fo:
        wr = csv.writer(fo); wr.writerow(['participante', 'x', 'y', 'inicio_ms', 'duracion_ms']); wr.writerows(filas)
    shutil.copy(os.path.join(src, img), os.path.join(out, base + os.path.splitext(img)[1]))
    print(base, w, h, 'fijaciones:', len(filas), 'participantes:', len({r[0] for r in filas}), 'fuera de imagen:', fuera)
