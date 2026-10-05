"""Render the OSLab authentication concept and its motion study without a browser."""

from functools import lru_cache
from pathlib import Path
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
FONT_ROOT = Path('C:/Windows/Fonts')
W, H, SCALE = 1600, 1000, 2
BG = '#FAFAF8'
INK = '#171A1D'
BODY = '#525962'
MUTED = '#777F86'
LINE = '#DADFE3'
BLUE = '#2059C9'
SAGE = '#4F8875'


@lru_cache(None)
def font(size, bold=False, latin=False):
    filename = ('segoeuib.ttf' if bold else 'segoeui.ttf') if latin else ('msyhbd.ttc' if bold else 'msyh.ttc')
    return ImageFont.truetype(str(FONT_ROOT / filename), int(size * SCALE))


class Surface:
    def __init__(self):
        self.image = Image.new('RGB', (W * SCALE, H * SCALE), BG)
        self.draw = ImageDraw.Draw(self.image)

    def text(self, x, y, value, size=16, color=INK, bold=False, latin=False, align='left'):
        ft = font(size, bold, latin)
        length = self.draw.textlength(str(value), font=ft) / SCALE
        if align == 'right':
            x -= length
        elif align == 'center':
            x -= length / 2
        self.draw.text((round(x * SCALE), round(y * SCALE)), str(value), font=ft, fill=color, anchor='lt')

    def rect(self, x, y, w, h, fill, radius=0, stroke=None, width=1):
        self.draw.rounded_rectangle((x*SCALE, y*SCALE, (x+w)*SCALE, (y+h)*SCALE),
                                    radius*SCALE, fill=fill, outline=stroke, width=max(1, round(width*SCALE)))

    def line(self, points, color=LINE, width=1):
        self.draw.line([(round(x*SCALE), round(y*SCALE)) for x,y in points], fill=color,
                       width=max(1, round(width*SCALE)), joint='curve')

    def circle(self, x, y, radius, fill=None, stroke=None, width=1):
        self.draw.ellipse(((x-radius)*SCALE, (y-radius)*SCALE, (x+radius)*SCALE, (y+radius)*SCALE),
                          fill=fill, outline=stroke, width=max(1, round(width*SCALE)))


def cubic(a, b, c, d, n=100):
    result=[]
    for t in np.linspace(0, 1, n):
        result.append(tuple((1-t)**3*a[k] + 3*(1-t)**2*t*b[k] + 3*(1-t)*t*t*c[k] + t**3*d[k] for k in range(2)))
    return result


ANCHORS=[(-100,235),(74,195),(270,98),(485,196),(683,100),(788,234),(1010,257)]
LABELS=[(74,195,'虚拟内存'),(270,98,'页表'),(485,196,'缺页中断'),(683,100,'LRU')]


def river(offset=0, t=0):
    points=[]
    for i in range(len(ANCHORS)-1):
        ax,ay=ANCHORS[i]
        bx,by=ANCHORS[i+1]
        wave = math.sin(t*math.tau + i*.65)*1.5
        a=(ax, ay+offset+wave)
        d=(bx, by+offset+wave)
        b=(ax+(bx-ax)*.40, ay+offset)
        c=(bx-(bx-ax)*.40, by+offset)
        points.extend(cubic(a,b,c,d,90)[0 if i==0 else 1:])
    return points


def flow(surface, phase=0, ox=110, oy=470):
    def translated(points):return [(x+ox,y+oy) for x,y in points]
    center=river(0,phase)
    for i in range(-12,13):
        color='#CBD2D8' if i%4==0 else '#E1E5E8'
        surface.line(translated(river(i*2.6,phase)),color,.65 if i%4 else .9)
    for i in (-4,4):
        surface.line(translated(river(i*2.6,phase)),'#CDD9F1',.9)
    paths=[center,river(10.4,phase)]
    for j,path in enumerate(paths):
        # Short travelling strokes, not a random particle field.
        head=(phase+.14+j*.37)%1
        for k in range(19):
            fraction=head-k*.0028
            if fraction<0:continue
            idx=min(len(path)-2,int(fraction*(len(path)-1)))
            opacity=(1-k/19)**1.2
            start=path[idx]
            end=path[idx+1]
            base=np.array([250,250,248]);accent=np.array([32,89,201])
            color=tuple(int(v) for v in base*(1-opacity)+accent*opacity)
            surface.line(translated([start,end]),color,2.4 if j==0 else 1.2)
    for i,(x,y,label) in enumerate(LABELS):
        dist=abs(((phase+.14)-(.15+i*.175))%1)
        pulse=max(0,1-min(dist,1-dist)/.09)
        radius=12+pulse*6
        surface.circle(x+ox,y+oy,radius,BG,'#CCD4DE',.8)
        surface.circle(x+ox,y+oy,4.5,BLUE if pulse>.25 else INK)
        surface.text(x+ox,y+oy+28,label,14,BLUE if label=='LRU' else BODY,label=='LRU',align='center')
    # One prerequisite branch and one application branch keep the image curricular.
    branch=cubic((270,98),(324,95),(320,260),(398,266),60)
    surface.line(translated(branch),'#D0D8D6',.8)
    surface.circle(ox+398,oy+266,4,SAGE)
    surface.text(ox+418,oy+258,'地址转换',12,MUTED)
    branch=cubic((683,100),(792,100),(705,27),(782,27),60)
    surface.line(translated(branch),'#CFD7DE',.8)
    surface.circle(ox+782,oy+27,3.5,SAGE)
    surface.text(ox+798,oy+19,'页面置换实验',12,MUTED)


def logo(surface,x,y,size=34):
    # Reproduces the existing project logo, including its two original colors.
    surface.rect(x,y,size,size,None,6,'#A32A1F',2)
    surface.line([(x+size*.28,y+size*.64),(x+size*.73,y+size*.20)],'#E09527',2.3)
    surface.line([(x+size*.28,y+size*.78),(x+size*.73,y+size*.78)],'#A32A1F',2.3)


def frame(mode='login',phase=.34):
    s=Surface()
    logo(s,76,32,32)
    s.text(121,34,'OSLab',23,INK,True,True)
    s.line([(212,35),(212,61)],LINE)
    s.text(233,42,'操作系统课程设计实验平台',12,BODY)
    s.text(1329,43,'平台简介',13,BODY)
    s.text(1485,43,'进入学习',13,INK,align='right')
    s.line([(1498,49),(1512,49)],INK,1.2)
    s.line([(1507,44),(1512,49),(1507,54)],INK,1.2)
    s.text(112,171,'OPERATING SYSTEMS / LEARNING LAB',11,MUTED,latin=True)
    s.text(106,201,'OSLab',94,INK,True,True)
    s.text(113,327,'操作系统智能学习平台',34,INK,True)
    s.text(115,397,'连接知识图谱、概念练习与编程实验，',17,BODY)
    s.text(115,426,'让每一次学习，都有清晰的下一步。',17,BODY)
    flow(s,phase)
    s.line([(115,805),(136,805)],BLUE,2)
    s.text(149,798,'从理解一个知识点，到完成一次实验。',12,MUTED)
    # A quiet form shares the same open page; it has no floating card chrome.
    x=1090;w=386
    s.text(x,194,'登录',15,INK if mode=='login' else MUTED,mode=='login')
    s.text(x+87,194,'注册',15,INK if mode=='register' else MUTED,mode=='register')
    s.rect(x+(0 if mode=='login' else 87),226,31,2,BLUE,1)
    s.text(x,264,'继续你的学习' if mode=='login' else '创建你的学习账号',29,INK,True)
    s.text(x,309,'登录 OSLab，接着探索你的知识路径。' if mode=='login' else '从一个知识点开始，记录你的每次进步。',13,MUTED)
    fields=[('账号','输入账号'),('密码','输入密码')]
    if mode=='register':fields.append(('确认密码','再次输入密码'))
    for i,(label,hint) in enumerate(fields):
        y=366+i*101
        s.text(x,y,label,13,INK)
        s.rect(x,y+29,w,52,'#FFFFFF',6,LINE)
        s.text(x+17,y+47,hint,14,'#959DA5')
        if i==1:s.text(x+w-18,y+48,'显示',11,MUTED,align='right')
    if mode=='login':
        s.rect(x,585,14,14,'#FFFFFF',3,'#B6BEC7')
        s.text(x+24,584,'记住账号',12,BODY)
        button_y=629
    else:
        s.text(x,664,'账号至少 4 位，密码至少 8 位。',11,MUTED)
        button_y=704
    s.rect(x,button_y,w,52,INK,6)
    s.text(x+24,button_y+17,'登录' if mode=='login' else '创建账号',15,'#FFFFFF',True)
    s.line([(x+w-40,button_y+26),(x+w-24,button_y+26)],'#FFFFFF',1.3)
    s.line([(x+w-29,button_y+21),(x+w-24,button_y+26),(x+w-29,button_y+31)],'#FFFFFF',1.3)
    s.text(x,button_y+77,'账号用于保存你的练习、实验与学习记录。',11,MUTED)
    s.line([(112,873),(1488,873)],LINE)
    for xx,n,title,desc in [(112,'01','知识图谱','在关系中理解知识'),(585,'02','普通题与实验','用练习连接理解与实践'),(1058,'03','学习轨迹','让每一次进步有迹可循')]:
        s.text(xx,899,n,12,BLUE,latin=True)
        s.text(xx+34,896,title,15,INK,True)
        s.text(xx+34,924,desc,12,MUTED)
    s.text(112,977,'OSLab · 操作系统学习与实践',10,MUTED)
    s.text(1488,977,'DESIGN STUDY / 2026',10,MUTED,latin=True,align='right')
    return s.image


def render():
    OUT.mkdir(parents=True,exist_ok=True)
    for mode in ('login','register'):
        image=frame(mode)
        image.save(OUT/f'auth-{mode}.png')
        image.resize((1600,1000),Image.Resampling.LANCZOS).save(OUT/f'auth-{mode}-preview.png')
    frames=[]
    for i in range(72):
        image=frame('login',i/72).resize((1120,700),Image.Resampling.LANCZOS)
        frames.append(image)
    palette=frames[0].quantize(colors=192,method=Image.Quantize.MEDIANCUT)
    quantized=[image.quantize(palette=palette,dither=Image.Dither.NONE) for image in frames]
    quantized[0].save(OUT/'auth-motion.gif',save_all=True,append_images=quantized[1:],
                      duration=100,loop=0,optimize=True,disposal=1)
    # Data-driven verification of the domain graphic and of temporal variation.
    array0=np.asarray(frames[0]);array18=np.asarray(frames[18])
    changed=int(np.count_nonzero(np.any(array0!=array18,axis=2)))
    with Image.open(OUT/'auth-motion.gif') as gif:
        print(f'GIF: {gif.size}, {gif.n_frames} frames; changed pixels 0->18: {changed}')
    print('PNG: 3200 x 2000. Output:',OUT)


if __name__=='__main__':
    render()
