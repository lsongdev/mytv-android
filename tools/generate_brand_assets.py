# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow==13.0.0", "fonttools==4.68.0"]
# ///
"""Generate OhMyTV vector sources and Android exports. No font file is bundled."""
from pathlib import Path
import argparse
from xml.sax.saxutils import escape
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
OUT = ROOT / 'design/branding'
BG, MINT, WHITE, CORAL = '#14263D', '#69E1CF', '#F8F6F0', '#FF9A76'
OUTER = 'M35,36 H73 Q83,36 83,46 V67 Q83,77 73,77 H35 Q25,77 25,67 V46 Q25,36 35,36 Z'
INNER = 'M37,42 H71 Q77,42 77,48 V65 Q77,71 71,71 H37 Q31,71 31,65 V48 Q31,42 37,42 Z'
PLAY = 'M48,48 Q48,46 50,47 L63,55 Q65,56.5 63,58 L50,66 Q48,67 48,65 Z'
STAND = 'M50,77 H58 V81 H63 Q65,81 65,83 Q65,85 63,85 H45 Q43,85 43,83 Q43,81 45,81 H50 Z'
ANTENNA = 'M40,28 L54,38 L68,28'

parser = argparse.ArgumentParser()
parser.add_argument('--font', default='/System/Library/Fonts/Avenir Next.ttc')
parser.add_argument('--font-index', type=int, default=0)
args = parser.parse_args()
font = TTFont(args.font, fontNumber=args.font_index)
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
units = font['head'].unitsPerEm


def word_paths():
    x = 74.0
    paths = []
    for text, color in [('OhMy', WHITE), ('TV', MINT)]:
        pen = SVGPathPen(glyphs)
        for char in text:
            name = cmap[ord(char)]
            glyphs[name].draw(TransformPen(pen, (18 / units, 0, 0, -18 / units, x, 52)))
            x += glyphs[name].width * 18 / units
        paths.append((pen.getCommands(), color))
    return paths

WORDS = word_paths()


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(data)


def svg_mark():
    return f'''<path d="{ANTENNA}" fill="none" stroke="{MINT}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{STAND}" fill="{MINT}"/><path d="{OUTER}" fill="{MINT}"/>
<path d="{INNER}" fill="{BG}"/><path d="{PLAY}" fill="{WHITE}"/>
<circle cx="73" cy="74" r="1.4" fill="{CORAL}"/>'''


def android_mark(mono=False):
    if mono:
        return f'''<path android:pathData="{ANTENNA}" android:strokeColor="#FFFFFF" android:strokeWidth="5" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="@android:color/transparent"/>
<path android:pathData="{OUTER} {INNER}" android:fillType="evenOdd" android:fillColor="#FFFFFF"/>
<path android:pathData="{STAND} {PLAY}" android:fillColor="#FFFFFF"/>'''
    return f'''<path android:pathData="{ANTENNA}" android:strokeColor="{MINT}" android:strokeWidth="5" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="@android:color/transparent"/>
<path android:pathData="{STAND} {OUTER}" android:fillColor="{MINT}"/>
<path android:pathData="{INNER}" android:fillColor="{BG}"/>
<path android:pathData="{PLAY}" android:fillColor="{WHITE}"/>
<path android:pathData="M74.4,74 A1.4,1.4 0,1 0,71.6,74 A1.4,1.4 0,1 0,74.4,74" android:fillColor="{CORAL}"/>'''


def vector(body, width=108, height=108, viewport=None):
    vw, vh = viewport or (width, height)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width}dp" android:height="{height}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}">
{body}
</vector>
'''


def svg(body, width, height):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}">\n{body}\n</svg>\n'


def draw_mark(image, offset=(0, 0), scale=1):
    # Supersampled geometric exports mirror the vector master.
    draw = ImageDraw.Draw(image)
    def box(x0, y0, x1, y1): return (offset[0] + x0 * scale, offset[1] + y0 * scale, offset[0] + x1 * scale, offset[1] + y1 * scale)
    draw.line([box(40,28,54,38)[:2], box(54,38,68,28)[:2], box(68,28,68,28)[:2]], fill=MINT, width=max(1,round(5*scale)), joint='curve')
    for x,y in [(40,28),(54,38),(68,28)]: draw.ellipse(box(x-2.5,y-2.5,x+2.5,y+2.5),fill=MINT)
    draw.rectangle(box(50,76,58,82),fill=MINT)
    draw.rounded_rectangle(box(43,81,65,85),radius=2*scale,fill=MINT)
    draw.rounded_rectangle(box(25,36,83,77),radius=10*scale,fill=MINT)
    draw.rounded_rectangle(box(31,42,77,71),radius=6*scale,fill=BG)
    draw.polygon([box(48,47,48,47)[:2],box(48,66,48,66)[:2],box(65,56.5,65,56.5)[:2]],fill=WHITE)
    draw.ellipse(box(71.6,72.6,74.4,75.4),fill=CORAL)


def square(size, shape='square'):
    factor=4
    im=Image.new('RGBA',(size*factor,size*factor),BG)
    scale=size*factor/90
    draw_mark(im,(-9*scale,-9*scale),scale)
    if shape!='square':
        mask=Image.new('L',im.size,0); d=ImageDraw.Draw(mask)
        if shape=='round': d.ellipse((0,0,im.width-1,im.height-1),fill=255)
        else: d.rounded_rectangle((0,0,im.width-1,im.height-1),radius=im.width*.22,fill=255)
        im.putalpha(mask)
    return im.resize((size,size),Image.Resampling.LANCZOS)


def banner(width):
    factor=4; height=round(width*9/16); scale=width*factor/160
    im=Image.new('RGBA',(width*factor,height*factor),BG)
    draw_mark(im,(11*scale,12*scale),.6*scale)
    pilfont=ImageFont.truetype(args.font,round(18*scale),index=args.font_index,layout_engine=ImageFont.Layout.BASIC)
    d=ImageDraw.Draw(im)
    d.text((74*scale,52*scale),'OhMy',font=pilfont,fill=WHITE,anchor='ls')
    advance=d.textlength('OhMy',font=pilfont)
    d.text((74*scale+advance,52*scale),'TV',font=pilfont,fill=MINT,anchor='ls')
    return im.resize((width,height),Image.Resampling.LANCZOS)


OUT.mkdir(parents=True,exist_ok=True)
write(OUT/'icon.svg',svg(f'<rect width="108" height="108" fill="{BG}"/><g transform="translate(-10.8,-10.8) scale(1.2)">{svg_mark()}</g>',108,108))
write(OUT/'foreground.svg',svg(f'<g transform="translate(10.8,10.8) scale(.8)">{svg_mark()}</g>',108,108))
banner_svg=f'<rect width="160" height="90" fill="{BG}"/><g transform="translate(11,12) scale(.6)">{svg_mark()}</g>'+''.join(f'<path d="{p}" fill="{c}"/>' for p,c in WORDS)
write(OUT/'banner.svg',svg(banner_svg,160,90))
for shape in ['square','round','rounded']:
    square(512,shape).save(OUT/f'icon-{shape}-512.png')
banner(1280).save(OUT/'banner-1280x720.png')

write(RES/'drawable/ic_launcher_foreground.xml',vector(f'<group android:pivotX="54" android:pivotY="54" android:scaleX=".8" android:scaleY=".8">{android_mark()}</group>'))
write(RES/'drawable/ic_launcher_monochrome.xml',vector(f'<group android:pivotX="54" android:pivotY="54" android:scaleX=".8" android:scaleY=".8">{android_mark(True)}</group>'))
write(RES/'drawable/ohmytv_mark.xml',vector(f'<group android:pivotX="54" android:pivotY="54" android:scaleX="1.2" android:scaleY="1.2">{android_mark()}</group>'))
write(RES/'drawable/ohmytv_logo.xml',vector(f'<path android:pathData="M0,0H108V108H0Z" android:fillColor="{BG}"/><group android:pivotX="54" android:pivotY="54" android:scaleX="1.2" android:scaleY="1.2">{android_mark()}</group>'))
words_xml=''.join(f'<path android:pathData="{p}" android:fillColor="{c}"/>' for p,c in WORDS)
banner_xml=f'<group android:translateX="40" android:translateY="22.5"><group android:translateX="11" android:translateY="12" android:scaleX=".6" android:scaleY=".6">{android_mark()}</group>{words_xml}</group>'
write(RES/'drawable/tv_banner_foreground.xml',vector(banner_xml,240,135))
for dpi,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
    dest=RES/f'mipmap-{dpi}';dest.mkdir(exist_ok=True)
    square(round(80*scale)).save(dest/'ic_launcher.png')
    banner(round(160*scale)).save(dest/'tv_banner.png')
for version in [26,33]:
    mono='<monochrome android:drawable="@drawable/ic_launcher_monochrome" />' if version>=33 else ''
    write(RES/f'mipmap-anydpi-v{version}/ic_launcher.xml',f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    {mono}
</adaptive-icon>
''')
write(RES/'mipmap-anydpi-v26/tv_banner.xml','''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/tv_banner_foreground" />
</adaptive-icon>
''')
# Review sheet, kept outside APK resources.
board=Image.new('RGB',(1200,760),'#F1F2F4');d=ImageDraw.Draw(board)
label=ImageFont.truetype(args.font,24,index=args.font_index)
for x,shape,title in [(56,'square','Square'),(320,'round','Round'),(584,'rounded','Adaptive')]:
    im=square(190,shape);board.paste(im,(x,42),im)
    d.text((x,254),title,font=label,fill=BG)
b= banner(640);board.paste(b,(56,340),b)
d.text((740,368),'OhMyTV',font=ImageFont.truetype(args.font,48,index=args.font_index),fill=BG)
d.text((740,444),'TV banner / 16:9',font=label,fill=BG)
d.text((740,486),'Solid background',font=label,fill=BG)
d.text((740,528),'Vector master',font=label,fill=BG)
d.text((740,570),'Android 7+',font=label,fill=BG)
board.save(OUT/'preview.png')
print('Generated Android resources and design/branding exports')
