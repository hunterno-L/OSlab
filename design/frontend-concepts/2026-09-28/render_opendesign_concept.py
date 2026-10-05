from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from functools import lru_cache
import math

OUT = Path(__file__).resolve().parent
W, H = 2400, 1350
S = 2
FONT_DIR = Path('C:/Windows/Fonts')

# Grounded in the OpenDesign Mistral / Claude direction:
# warm neutral field, pure ink, restrained lines, a strict 4px rhythm.
BG = '#FAFAF8'
PAPER = '#FFFFFF'
INK = '#101214'
BODY = '#303330'
MUTED = '#858780'
LINE = '#D9DAD4'
BLUE = '#2059C9'          # OSLab's retained brand cue
BLUE_SOFT = '#EAF0FD'
SAGE = '#4F8875'
SAGE_SOFT = '#E9F1ED'
AMBER = '#B98239'
AMBER_SOFT = '#F6EDDF'
RED = '#A9695D'
RED_SOFT = '#F5EAE7'
SLATE = '#70808B'

@lru_cache(None)
def font(size, bold=False, mono=False):
    if mono:
        name = 'consolab.ttf' if bold else 'consola.ttf'
    else:
        name = 'msyhbd.ttc' if bold else 'msyh.ttc'
    return ImageFont.truetype(str(FONT_DIR / name), size * S)

class Draw:
    def __init__(self):
        self.im = Image.new('RGB', (W * S, H * S), BG)
        self.d = ImageDraw.Draw(self.im)

    def rect(self, x, y, w, h, fill, radius=0, outline=None, width=1):
        self.d.rounded_rectangle((x*S, y*S, (x+w)*S, (y+h)*S), radius*S,
                                 fill=fill, outline=outline, width=max(1, width*S))

    def line(self, points, fill=LINE, width=1):
        self.d.line([(int(x*S), int(y*S)) for x, y in points], fill=fill,
                    width=max(1, int(width*S)), joint='curve')

    def text(self, x, y, value, size=16, color=INK, bold=False, mono=False,
             anchor='lt'):
        self.d.text((int(x*S), int(y*S)), value, font=font(size, bold, mono),
                    fill=color, anchor=anchor)

    def centered(self, x, y, value, size=16, color=INK, bold=False, mono=False):
        self.text(x, y, value, size, color, bold, mono, 'mm')

    def circle(self, x, y, r, fill, outline=None, width=1):
        self.d.ellipse(((x-r)*S, (y-r)*S, (x+r)*S, (y+r)*S), fill=fill,
                       outline=outline, width=max(1, int(width*S)))

    def pill(self, x, y, label, fill, color, width=None, h=28, border=None):
        width = width or self.d.textlength(label, font=font(12))/S + 24
        self.rect(x, y, width, h, fill, h//2, border)
        self.centered(x+width/2, y+h/2, label, 12, color)
        return width

    def rule(self, x, y, w, color=LINE, width=1):
        self.line([(x, y), (x+w, y)], color, width)

    def icon(self, x, y, kind, color=SLATE, size=18):
        k = size/18
        def ln(points, width=1.5): self.line([(x+a*k, y+b*k) for a,b in points], color, width)
        if kind == 'grid':
            for a,b in [(2,2),(11,2),(2,11),(11,11)]: self.rect(x+a*k,y+b*k,5*k,5*k,None,1,color,1)
        elif kind == 'nodes':
            ln([(4,4),(14,4),(9,14),(4,4),(14,4),(9,14)])
            for a,b in [(4,4),(14,4),(9,14)]: self.circle(x+a*k,y+b*k,2.2*k,PAPER,color,1)
        elif kind == 'book':
            ln([(2,3),(8,5),(8,16),(2,14),(2,3)]); ln([(8,5),(16,3),(16,14),(8,16)])
        elif kind == 'flask':
            ln([(6,2),(12,2),(11,7),(15,15),(3,15),(7,7),(6,2)])
        elif kind == 'chart':
            ln([(3,15),(3,5),(7,5),(7,15),(10,15),(10,2),(14,2),(14,15)])
        elif kind == 'user':
            self.circle(x+9*k,y+5*k,3*k,None,color,1); ln([(3,16),(5,12),(9,11),(13,12),(15,16)])
        elif kind == 'search':
            self.circle(x+7*k,y+7*k,4.5*k,None,color,1.5);ln([(11,11),(16,16)],1.5)
        elif kind == 'bell':
            ln([(4,14),(5,6),(8,3),(11,3),(14,6),(15,14),(4,14)]);ln([(7,17),(12,17)])
        elif kind == 'chev': ln([(6,4),(11,9),(6,14)])
        elif kind == 'play':
            self.d.polygon([(x+6*k*S,y+3*k*S),(x+6*k*S,y+15*k*S),(x+15*k*S,y+9*k*S)], fill=color)

def nav(d):
    d.rect(0,0,W,76,PAPER)
    d.rule(0,76,W,LINE)
    d.rect(36,21,34,34,BLUE,6)
    d.text(53,27,'O',20,PAPER,True,anchor='mm')
    d.text(84,24,'OSLab',22,INK,True)
    d.text(168,28,'操作系统课程设计实验平台',14,MUTED)
    items=[('首页',316),('课程学习',413),('知识图谱',526),('实验中心',642),('在线题库',757),('学习统计',870)]
    for label,x in items:
        active=label=='知识图谱'
        d.centered(x,37,label,15,BLUE if active else BODY,active)
        if active:d.rect(x-38,73,76,3,BLUE,1)
    d.icon(2027,27,'bell',MUTED,18)
    d.circle(2094,38,17,'#E7EDF8')
    d.centered(2094,38,'张',13,BLUE,True)
    d.text(2122,25,'张小明',14,INK,True)
    d.text(2122,46,'本科生 · 操作系统',10,MUTED)
    d.icon(2326,30,'chev',MUTED,16)

def sidebar(d):
    x0=0; w=244
    d.rect(x0,77,w,H-77,PAPER)
    d.rule(w,77,0,LINE)
    d.rect(24,108,196,78,'#F4F5F1',8)
    d.rect(39,126,32,42,'#D7E3F6',4)
    d.text(55,134,'OS',11,BLUE,True,anchor='mm')
    d.text(86,119,'操作系统',14,INK,True)
    d.text(86,146,'2024 秋学期',11,MUTED)
    d.icon(196,135,'chev',MUTED,15)
    sections=[('课程概览','grid'),('章节目录','book'),('知识图谱','nodes'),('我的学习','chart'),('实验任务','flask'),('在线题库','book'),('学习统计','chart')]
    for i,(label,kind) in enumerate(sections):
        y=224+i*49; active=label=='知识图谱'
        if active:
            d.rect(20,y-8,204,40,BLUE_SOFT,6)
            d.rect(20,y-8,3,40,BLUE,1)
        d.icon(39,y+4,kind,BLUE if active else MUTED,17)
        d.text(70,y-5,label,14,BLUE if active else BODY,active)
    d.rule(30,588,182,LINE)
    d.text(30,619,'我的课程进度',11,MUTED)
    d.text(30,648,'内存管理',15,INK,True)
    d.text(30,682,'本章已掌握',11,MUTED)
    d.text(189,681,'68%',12,BLUE,True,mono=True,anchor='rt')
    d.rect(30,703,180,5,'#E9ECE8',2)
    d.rect(30,703,122,5,BLUE,2)
    d.text(30,747,'学习状态',11,MUTED)
    for i,(label,col,num) in enumerate([('已掌握',SAGE,'18'),('待巩固',AMBER,'8'),('未学习','#B3BAB7','6')]):
        yy=777+i*27;d.circle(35,yy+6,4,col);d.text(48,yy,label,12,BODY);d.text(206,yy,num,12,MUTED,mono=True,anchor='rt')
    d.rule(30,912,182,LINE)
    d.icon(30,946,'user',MUTED,17);d.text(59,938,'个人中心',13,BODY)
    d.icon(30,989,'bell',MUTED,17);d.text(59,981,'帮助与反馈',13,BODY)

def curve(d,a,b,color=LINE,width=1):
    ax,ay=a; bx,by=b
    mid=(ax+bx)/2
    d.line([(ax,ay),(mid,ay),(mid,by),(bx,by)],color,width)

def node(d,x,y,w,label,status='done',selected=False,kind='concept'):
    if selected:
        d.rect(x-5,y-5,w+10,42,BLUE_SOFT,8)
        d.rect(x,y,w,32,PAPER,6,BLUE,2)
        d.rect(x+w-4,y+8,3,16,BLUE,1)
    else:
        fill=PAPER if status!='unseen' else '#F5F6F3'
        outline=LINE if not selected else BLUE
        d.rect(x,y,w,32,fill,6,outline)
    col= SAGE if status=='done' else AMBER if status=='review' else '#B5BDB8'
    d.circle(x+16,y+16,4,col)
    d.text(x+29,y+8,label,13,INK if selected else BODY,selected)

def graph(d):
    gx,gy,gw,gh=280,346,1280,770
    d.rect(gx,gy,gw,gh,PAPER,8,LINE)
    d.text(gx+30,gy+27,'课程知识结构',12,MUTED,True)
    d.text(gx+gw-30,gy+27,'32 个节点  ·  48 条关系',11,MUTED,anchor='rt')
    d.rule(gx+30,gy+61,gw-60,LINE)
    # A sparse, intentional learning graph: one prominent path, two related branches.
    P={
        'os':(760,430,160,'操作系统概述','done',False),
        'mem':(760,526,160,'内存管理','done',True),
        'virt':(424,640,146,'虚拟内存','done',False),
        'space':(420,750,146,'地址空间','done',False),
        'table':(630,640,122,'页表','done',False),
        'tlb':(600,817,122,'TLB','review',False),
        'fault':(835,640,146,'缺页中断','review',False),
        'replace':(1044,640,146,'页面置换','review',False),
        'lru':(1034,781,192,'LRU 页面置换算法','review',True),
        'fifo':(1265,640,120,'FIFO','done',False),
        'opt':(1265,817,120,'OPT','unseen',False),
        'sched':(520,468,146,'处理机调度','done',False),
        'file':(1087,468,146,'文件系统','unseen',False),
        'experiment':(1272,938,162,'页面置换实验','review',False),
    }
    edges=[('os','mem'),('os','sched'),('os','file'),('mem','virt'),('mem','table'),('mem','fault'),('space','virt'),('virt','table'),('table','tlb'),('table','fault'),('fault','replace'),('replace','fifo'),('replace','lru'),('replace','opt'),('lru','experiment')]
    for a,b in edges:
        x,y,w,_,s,sel=P[a]; x2,y2,w2,_,s2,sel2=P[b]
        color=BLUE if (a,b) in [('table','fault'),('fault','replace'),('replace','lru'),('lru','experiment')] else '#C5CBC7'
        width=2 if color==BLUE else 1
        curve(d,(x+w/2,y+32),(x2+w2/2,y2),color,width)
    d.pill(gx+30,gy+81,'我的掌握',BLUE_SOFT,BLUE,89,26)
    d.pill(gx+129,gy+81,'课程结构',BG,MUTED,89,26)
    d.text(gx+250,gy+87,'高亮学习路径：页表 → 缺页中断 → 页面置换 → LRU',12,MUTED)
    for _,(x,y,w,label,status,sel) in P.items(): node(d,x,y,w,label,status,sel)
    d.pill(902,715,'前置知识',BLUE_SOFT,BLUE,81,24)
    d.pill(1178,715,'算法分支',AMBER_SOFT,AMBER,81,24)
    d.rule(gx+30,gh+gy-62,gw-60,LINE)
    d.text(gx+34,gy+gh-43,'掌握状态',11,MUTED)
    for i,(lab,col) in enumerate([('已掌握',SAGE),('待巩固',AMBER),('未学习','#B5BDB8')]):
        xx=gx+124+i*112; d.circle(xx,gy+gh-37,4,col);d.text(xx+12,gy+gh-44,lab,11,MUTED)
    d.text(gx+gw-32,gy+gh-43,'滚轮缩放  ·  拖拽查看  ·  点击节点查看学习证据',11,MUTED,anchor='rt')

def detail(d):
    x,y,w,h=1592,346,746,770
    d.rect(x,y,w,h,PAPER,8,LINE)
    d.text(x+34,y+28,'知识点详情',12,MUTED,True)
    d.pill(x+w-125,y+18,'待巩固',AMBER_SOFT,AMBER,91,26)
    d.text(x+34,y+77,'LRU 页面置换算法',27,INK,True)
    d.text(x+34,y+118,'内存管理  /  页面置换',12,MUTED)
    d.rule(x+34,y+153,w-68,LINE)
    d.text(x+34,y+181,'最近最少使用（LRU）根据页面的',15,BODY)
    d.text(x+34,y+208,'最近访问顺序，优先淘汰最长时间未访问的页面。',15,BODY)
    d.text(x+34,y+245,'它的关键是：命中页面也要更新访问顺序。',15,BODY,True)
    d.text(x+34,y+302,'我的学习状态',12,MUTED,True)
    d.text(x+34,y+342,'概念理解',13,BODY);d.text(x+w-34,y+342,'54%',13,INK,True,mono=True,anchor='rt')
    d.rect(x+34,y+369,w-68,6,'#E8EBE5',3);d.rect(x+34,y+369,(w-68)*.54,6,BLUE,3)
    d.text(x+34,y+405,'实践应用',13,BODY);d.text(x+w-34,y+405,'22%',13,INK,True,mono=True,anchor='rt')
    d.rect(x+34,y+432,w-68,6,'#E8EBE5',3);d.rect(x+34,y+432,(w-68)*.22,6,SAGE,3)
    d.rule(x+34,y+480,w-68,LINE)
    d.text(x+34,y+511,'最近一次学习证据',12,MUTED,True)
    d.rect(x+34,y+543,w-68,90,RED_SOFT,6)
    d.pill(x+51,y+558,'普通题 · 判断',PAPER,RED,104,23)
    d.text(x+51,y+594,'命中后是否需要更新最近访问顺序？',14,BODY)
    d.text(x+w-51,y+594,'答错  ·  2 小时前',11,RED,anchor='rt')
    d.text(x+34,y+670,'推荐下一步',12,MUTED,True)
    d.text(x+34,y+704,'先做 3 道 LRU 概念练习，再进入实验。',15,BODY)
    d.text(x+34,y+731,'原因：最近一次错误集中在“命中后的顺序更新”。',12,MUTED)
    d.rect(x+34,y+759,w-68,48,BLUE,5)
    d.centered(x+w/2,y+783,'开始知识点练习',14,PAPER,True)
    d.rect(x+34,y+822,(w-85)/2,46,PAPER,5,LINE)
    d.centered(x+34+(w-85)/4,y+845,'进入页面置换实验',13,INK)
    d.rect(x+49+(w-85)/2,y+822,(w-85)/2,46,PAPER,5,LINE)
    d.centered(x+49+3*(w-85)/4,y+845,'向图谱导师提问',13,INK)

def render():
    d=Draw(); nav(d); sidebar(d)
    d.text(280,116,'知识图谱',33,INK,True)
    d.text(280,166,'探索课程结构，沿着你的薄弱点规划下一步学习。',15,MUTED)
    d.text(2324,121,'当前章节：',12,MUTED,anchor='rt')
    d.text(2324,121,'内存管理',12,BLUE,True,anchor='lt')
    # Page controls are compact and editorial; no dashboard clutter.
    d.rect(280,219,184,40,PAPER,5,LINE)
    d.text(300,231,'搜索知识点',13,MUTED)
    d.icon(434,229,'search',MUTED,16)
    for i,(label,xx,active) in enumerate([('课程全图',493,False),('我的掌握',607,True)]):
        d.text(xx,231,label,13,BLUE if active else MUTED,active)
        if active:d.rect(xx-3,256,72,2,BLUE,1)
    d.rect(701,219,138,40,PAPER,5,LINE);d.text(721,231,'全部章节',13,BODY);d.icon(812,229,'chev',MUTED,14)
    d.rect(851,219,138,40,PAPER,5,LINE);d.text(871,231,'全部类型',13,BODY);d.icon(962,229,'chev',MUTED,14)
    d.text(1506,231,'最近更新 · 6 条学习证据',12,MUTED,anchor='rt')
    graph(d); detail(d)
    d.text(280,1152,'OSLab  /  KNOWLEDGE GRAPH',10,MUTED)
    d.text(2324,1152,'个人掌握视图  ·  2024 秋学期',10,MUTED,anchor='rt')
    # Export at a practical 16:9 size while preserving enough text detail for review.
    out=OUT/'opendesign-knowledge-workspace.png'
    d.im.resize((1920,1080),Image.Resampling.LANCZOS).save(out)
    print(out)

if __name__=='__main__': render()
