"""OSLab interface concepts. Pure Pillow renderer; no browser or network."""
from pathlib import Path
from functools import lru_cache
import math
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
S = 2
W, H = 1600, 1040
BG = '#F3F6F7'
WHITE = '#FFFFFF'
INK = '#172C36'
MUTED = '#73848C'
LINE = '#E1E8EB'
TEAL = '#137D78'
MINT = '#E7F4F0'
GREEN = '#2C9D86'
AMBER = '#D39841'
RED = '#D87868'
NAVY = '#142A35'
FONT_DIR = Path('C:/Windows/Fonts')

@lru_cache(None)
def font(size=16, bold=False, latin=False):
    file = ('segoeuib.ttf' if bold else 'segoeui.ttf') if latin else ('msyhbd.ttc' if bold else 'msyh.ttc')
    return ImageFont.truetype(str(FONT_DIR / file), round(size*S))

class UI:
    def __init__(self, active, page, teacher=False):
        self.im = Image.new('RGB', (W*S,H*S), BG)
        self.d = ImageDraw.Draw(self.im)
        self.chrome(active,page,teacher)

    def box(self,x,y,w,h,fill=WHITE,r=12,stroke=None,width=1):
        self.d.rounded_rectangle((x*S,y*S,(x+w)*S,(y+h)*S),round(r*S),fill=fill,outline=stroke,width=round(width*S))

    def line(self,points,fill=LINE,width=1):
        self.d.line([(round(x*S),round(y*S)) for x,y in points],fill=fill,width=max(1,round(width*S)),joint='curve')

    def circle(self,x,y,r,fill,stroke=None,width=1):
        self.d.ellipse(((x-r)*S,(y-r)*S,(x+r)*S,(y+r)*S),fill=fill,outline=stroke,width=max(1,round(width*S)))

    def txt(self,x,y,text,size=16,color=INK,bold=False,latin=False,align='left'):
        ft=font(size,bold,latin)
        length=self.d.textlength(str(text),font=ft)
        if align=='center': x-=length/(2*S)
        elif align=='right': x-=length/S
        self.d.text((round(x*S),round(y*S)),str(text),font=ft,fill=color,anchor='lt')

    def lines(self,x,y,rows,size=16,color=INK,gap=27,bold=False):
        for i,row in enumerate(rows): self.txt(x,y+gap*i,row,size,color,bold)

    def card(self,x,y,w,h,fill=WHITE):
        self.box(x,y+2,w,h,'#EAF0F2',14)
        self.box(x,y,w,h,fill,14,LINE)

    def pill(self,x,y,label,fill=MINT,color=TEAL,width=None):
        width=width or self.d.textlength(label,font=font(12))/S+22
        self.box(x,y,width,26,fill,6)
        self.txt(x+width/2,y+6,label,12,color,align='center')
        return width

    def button(self,x,y,w,label,primary=True,height=42):
        self.box(x,y,w,height,TEAL if primary else WHITE,8,None if primary else LINE)
        self.txt(x+w/2,y+(height-16)/2,label,15,WHITE if primary else INK,bold=primary,align='center')

    def progress(self,x,y,w,value,color=TEAL,track='#E8EEEE',h=6):
        self.box(x,y,w,h,track,h/2)
        if value>0: self.box(x,y,w*value,h,color,h/2)

    def icon(self,x,y,name,color=MUTED,size=20):
        # Compact code-native line marks for the design proof.
        k=size/20
        def pts(a): return [(x+u*k,y+v*k) for u,v in a]
        def ln(a): self.line(pts(a),color,1.6)
        if name=='graph':
            for a in [[(4,5),(15,4)],[(4,5),(9,15)],[(15,4),(9,15)]]: ln(a)
            for u,v in [(4,5),(15,4),(9,15)]: self.circle(x+u*k,y+v*k,2.6*k,WHITE,color,1.5)
        elif name=='book':
            ln([(2,3),(9,5),(9,18),(2,16),(2,3)]);ln([(9,5),(17,3),(17,16),(9,18)])
        elif name=='grid':
            for u,v in [(2,2),(11,2),(2,11),(11,11)]: self.box(x+u*k,y+v*k,6*k,6*k,None,1,color,1.4)
        elif name=='code':
            ln([(6,5),(1,10),(6,15)]);ln([(14,5),(19,10),(14,15)]);ln([(12,3),(8,17)])
        elif name=='clock':
            self.circle(x+10*k,y+10*k,8*k,None,color,1.5);ln([(10,5),(10,10),(14,12)])
        elif name=='search':
            self.circle(x+8*k,y+8*k,5.5*k,None,color,1.5);ln([(12,12),(17,17)])
        elif name=='chevron': ln([(7,6),(11,10),(7,14)])
        elif name=='spark':
            ln([(10,1),(12,7),(18,10),(12,12),(10,19),(8,12),(2,10),(8,7),(10,1)])
        elif name=='bars':
            for u,yy in [(3,11),(9,6),(15,2)]: ln([(u,18),(u,yy),(u+3,yy),(u+3,18)])
        elif name=='check': ln([(3,10),(8,15),(17,5)])
        elif name=='arrow': ln([(2,10),(17,10)]);ln([(12,5),(17,10),(12,15)])
        elif name=='bell':
            ln([(4,14),(5,6),(8,3),(12,3),(15,6),(16,14),(4,14)]);ln([(8,17),(12,17)])

    def chrome(self,active,page,teacher):
        self.box(0,0,1600,74,WHITE,0)
        self.box(0,74,212,966,WHITE,0)
        self.line([(212,0),(212,1040)])
        self.line([(0,74),(1600,74)])
        self.box(25,21,34,34,TEAL,10)
        self.txt(42,28,'O',20,WHITE,True,True,'center')
        self.txt(71,22,'OSLab',23,INK,True,True)
        self.txt(238,29,'操作系统 · 智能学习实验室',15,MUTED)
        self.box(1100,19,253,36,'#F4F6F7',8)
        self.icon(1111,28,'search',MUTED,16)
        self.txt(1136,30,'搜索知识点、题目与实验',12,MUTED)
        self.icon(1382,28,'bell',MUTED,18)
        self.circle(1441,37,16,'#DFEBE8')
        self.txt(1441,28,'陈' if teacher else '林',14,TEAL,True,align='center')
        self.txt(1468,25,'陈老师' if teacher else '林同学',14,INK,True)
        self.txt(1468,46,'教师空间' if teacher else '学习空间',10,MUTED)
        self.txt(26,100,'TEACHING' if teacher else 'WORKSPACE',10,MUTED,True,True)
        nav= [('教学概览','grid'),('班级知识图谱','graph'),('题目管理','book'),('实验管理','code'),('学习记录','clock')] if teacher else [('学习概览','grid'),('知识图谱','graph'),('知识点练习','book'),('编程实验','code'),('成长记录','clock')]
        for i,(label,ic) in enumerate(nav):
            y=130+49*i
            if label==active:
                self.box(14,y-2,184,42,MINT,8)
                self.box(14,y+10,3,17,TEAL,1)
            self.icon(27,y+8,ic,TEAL if label==active else MUTED,18)
            self.txt(57,y+10,label,14,TEAL if label==active else '#627780',label==active)
        self.line([(24,389),(187,389)])
        self.txt(26,413,'我的班级' if teacher else '课程章节',11,MUTED)
        chapters=['软件工程 2301','计算机科学 2302','网络工程 2301'] if teacher else ['操作系统概述','进程与线程','处理机调度','内存管理','文件系统','I/O 管理']
        for i,label in enumerate(chapters):
            y=450+39*i
            selected=(i==0 if teacher else i==3)
            if selected:self.box(22,y-7,168,32,'#F0F5F4',6)
            self.txt(28,y,str(i+1).zfill(2),11,TEAL if selected else '#A2AFB5',latin=True)
            self.txt(56,y,label,13,TEAL if selected else MUTED,selected)
            if selected:self.circle(179,y+8,2.5,TEAL)
        self.box(22,817,169,115,'#F5F8F7',10)
        self.txt(36,835,'本周教学' if teacher else '今天的学习',12,MUTED)
        self.txt(36,859,'3 个班级' if teacher else '20',23,INK,True,True)
        if not teacher:self.txt(74,869,'/ 30 分钟',12,MUTED)
        self.progress(36,900,137,.75 if teacher else 2/3)
        self.icon(27,973,'spark',TEAL,17)
        self.txt(54,974,'图谱导师',13,INK)
        self.txt(238,1018,'OSLab  /  INTERFACE CONCEPT',10,MUTED,latin=True)
        self.txt(1571,1018,'设计预览 · 示例数据   /   '+page,10,MUTED,align='right')

    def heading(self,title,subtitle,eyebrow):
        self.txt(240,99,eyebrow,12,MUTED)
        self.txt(238,128,title,30,INK,True)
        self.txt(240,174,subtitle,14,MUTED)

    def save(self,name):
        self.im.save(OUT/(name+'.png'))
        self.im.resize((1600,1040),Image.Resampling.LANCZOS).save(OUT/(name+'-preview.png'))
        return self.im

def dark_graph(ui,x,y,w,h):
    ui.box(x,y,w,h,NAVY,14)
    for xx in range(x+18,x+w-12,25):
        for yy in range(y+18,y+h-12,25): ui.circle(xx,yy,.65,'#29414B')

def curve(ui,a,b,color,width=1.5):
    ax,ay=a;bx,by=b
    points=[]
    for i in range(31):
        t=i/30
        points.append(((1-t)**3*ax+3*(1-t)**2*t*(ax+bx)/2+3*(1-t)*t*t*(ax+bx)/2+t**3*bx,(1-t)**3*ay+3*(1-t)**2*t*ay+3*(1-t)*t*t*by+t**3*by))
    ui.line(points,color,width)

def graph_screen():
    u=UI('知识图谱','01')
    u.heading('我的知识图谱','从一个知识点出发，找到属于你的学习路径。','学习空间  /  操作系统')
    u.box(1273,124,298,42,WHITE,8,LINE)
    u.box(1277,128,140,34,TEAL,6)
    u.txt(1347,137,'个人掌握',14,WHITE,True,align='center')
    u.txt(1492,137,'课程全图',14,MUTED,align='center')
    for xx,num,lab,col in [(240,'18','已掌握',GREEN),(436,'8','待巩固',AMBER),(626,'6','未探索','#9CAAB0')]:
        u.circle(xx+4,215,4,col);u.txt(xx+19,203,num,25,INK,True,True);u.txt(xx+64,214,lab,13,MUTED)
    u.txt(1194,214,'32 个知识点 · 48 条学习关系',12,MUTED,align='right')
    dark_graph(u,238,252,958,688)
    u.box(260,273,264,38,'#233B45',7)
    u.icon(272,284,'search','#98B0B6',16);u.txt(299,285,'在图谱中定位知识点',13,'#B0C1C7')
    u.box(891,273,137,36,'#22433F',7);u.txt(959,284,'前置学习路径',12,'#91D6C0',align='center')
    u.box(1039,273,132,36,'#233B45',7);u.txt(1105,284,'只看待巩固',12,'#BDCDD3',align='center')
    u.txt(288,348,'进程与调度',11,'#7A939E')
    u.txt(700,348,'内存管理',11,'#7A939E')
    nodes={
      '操作系统':(590,477,35,'#87A9BC'),
      '进程':(398,397,25,'#64BFA6'),
      '进程状态':(305,485,18,'#64BFA6'),
      'CPU 调度':(379,584,26,'#C5A069'),
      '上下文切换':(478,343,17,'#64BFA6'),
      '虚拟内存':(718,410,33,'#64BFA6'),
      '页表':(578,641,27,'#64BFA6'),
      'TLB':(415,737,19,'#C5A069'),
      '缺页中断':(740,621,29,'#C5A069'),
      '页面置换':(889,493,32,'#64BFA6'),
      'FIFO':(1075,438,23,'#64BFA6'),
      'LRU':(968,653,45,'#E7AE61'),
      'OPT':(1100,782,23,'#7B939D'),
      '工作集':(749,806,20,'#7B939D'),
      '文件系统':(301,776,23,'#7B939D')}
    edges=[('操作系统','进程'),('进程','进程状态'),('进程','CPU 调度'),('进程','上下文切换'),('操作系统','虚拟内存'),('操作系统','文件系统'),('虚拟内存','页表'),('页表','TLB'),('页表','缺页中断'),('缺页中断','页面置换'),('页面置换','FIFO'),('页面置换','LRU'),('页面置换','OPT'),('缺页中断','工作集')]
    high=[('页表','缺页中断'),('缺页中断','页面置换'),('页面置换','LRU')]
    for a,b in edges:curve(u,nodes[a][:2],nodes[b][:2],'#6CBCAA' if (a,b) in high else '#39505B',2.1 if (a,b) in high else 1.3)
    u.pill(756,529,'建议补强路径','#214940','#A2E1CC',119)
    for label,(xx,yy,rr,col) in nodes.items():
        if label=='LRU':
            u.circle(xx,yy,rr+16,None,'#574B3A',1)
            u.circle(xx,yy,rr+9,None,'#A07F4D',1)
            u.circle(xx,yy,rr,'#3D3831',col,2)
            u.txt(xx,yy-22,'LRU',22,'#F2C68C',True,True,'center')
            u.txt(xx,yy+8,'38%',14,'#E5BD86',latin=True,align='center')
            u.txt(xx,yy+71,'当前薄弱点',12,'#E6BD87',align='center')
        else:
            u.circle(xx,yy,rr+5,'#1B3540')
            u.circle(xx,yy,rr,'#243F49',col,1.6)
            u.circle(xx,yy,4,col)
            u.txt(xx,yy+rr+11,label,13,'#BFD1D7',align='center')
    for i,(lab,col) in enumerate([('已掌握','#64BFA6'),('巩固中','#C5A069'),('薄弱','#E7AE61'),('未探索','#7B939D')]):
        xx=264+i*105;u.circle(xx+5,909,3.5,col);u.txt(xx+18,903,lab,11,'#A9BFC6')
    u.txt(1167,904,'−       100%       +',12,'#A9BFC6',latin=True,align='right')
    u.card(1216,252,356,688)
    u.txt(1240,277,'当前选中',12,MUTED);u.pill(1481,271,'待补强','#FFF0DA','#A56E20',67)
    u.txt(1240,310,'LRU 页面置换',23,INK,True)
    u.txt(1240,348,'最近最少使用页面置换算法',13,MUTED)
    u.circle(1300,425,43,None,'#E9EFED',7)
    u.d.arc((1257*S,382*S,1343*S,468*S),-90,46.8,fill=AMBER,width=7*S)
    u.txt(1300,402,'38',29,INK,True,True,'center');u.txt(1300,438,'掌握程度',10,MUTED,align='center')
    u.txt(1371,394,'概念理解',12,MUTED);u.txt(1538,393,'54%',13,INK,True,True,'right')
    u.progress(1371,418,166,.54,AMBER)
    u.txt(1371,441,'实践应用',12,MUTED);u.txt(1538,440,'22%',13,INK,True,True,'right')
    u.progress(1371,465,166,.22,TEAL)
    u.txt(1240,492,'6 条学习证据  ·  刚刚更新',11,MUTED)
    u.line([(1240,521),(1548,521)])
    u.pill(1240,539,'最近一次错因','#FAEEE9','#B57151',104)
    u.lines(1240,580,['命中页面后，没有更新','最近访问顺序。'],17,INK,gap=28,bold=True)
    u.txt(1240,646,'来自：LRU 概念判断题 #K012',11,MUTED)
    u.button(1240,686,308,'继续知识点练习    →')
    u.button(1240,740,308,'打开页面置换实验室',False)
    u.txt(1240,814,'推荐补强顺序',12,MUTED)
    for i,(label,col) in enumerate([('缺页中断',GREEN),('LRU 原理',AMBER),('代码实现','#BDCBCF')]):
        xx=1256+i*111
        if i<2:u.line([(xx+8,855),(xx+103,855)],LINE,2)
        u.circle(xx,855,5,col)
        u.txt(xx-15,873,label,11,MUTED)
    u.icon(244,966,'clock',TEAL,16)
    u.txt(270,967,'学习动态',12,INK,True)
    u.txt(349,967,'刚刚完成 LRU 判断题 · 已记录错因，推荐 4 道巩固练习',12,MUTED)
    u.txt(1188,967,'查看成长记录  →',12,TEAL,align='right')
    return u.save('01-knowledge-graph')

def practice_screen():
    u=UI('知识点练习','02')
    u.heading('LRU 页面置换','理解淘汰规则，也理解每一次命中如何改变顺序。','知识图谱  /  内存管理  /  知识点工作台')
    u.pill(1370,135,'概念理解 54%',MINT,TEAL,148)
    for i,(label,x) in enumerate([('概念速览',240),('知识点练习  8',367),('关联实验  2',540),('学习证据  6',693)]):
        u.txt(x,219,label,14,TEAL if i==1 else MUTED,i==1)
        if i==1:u.box(x,249,130,3,TEAL,1)
    u.line([(238,253),(1572,253)])
    u.card(238,276,854,593)
    u.txt(266,303,'知识点练习',17,INK,True)
    u.txt(1064,306,'03 / 08',14,MUTED,latin=True,align='right')
    for x,label,sel in [(266,'判断',True),(339,'单选',False),(412,'多选',False),(485,'填空',False)]:
        u.pill(x,343,label,MINT if sel else '#F4F6F7',TEAL if sel else MUTED,62)
    u.txt(266,392,'判断题  ·  概念辨析',12,MUTED)
    u.lines(266,428,['当一个已在内存中的页面被再次访问时，','LRU 不需要更新页面的最近使用顺序。'],24,INK,38,True)
    for xx,label,selected,col in [(266,'正确',True,RED),(679,'错误',False,GREEN)]:
        u.box(xx,532,385,66,'#FDF2EF' if selected else '#F0F8F5',10,'#E4AAA0' if selected else '#A8CDBC')
        u.circle(xx+26,565,9,RED if selected else WHITE,RED if selected else GREEN,1.5)
        if selected:u.circle(xx+26,565,3,WHITE)
        u.txt(xx+50,554,label,17,INK)
        u.txt(xx+365,558,'你的选择' if selected else '正确答案',12,col,align='right')
    u.box(266,627,798,127,'#F7F9F8',10)
    u.circle(287,652,8,RED);u.txt(287,644,'×',14,WHITE,True,align='center')
    u.txt(307,643,'还差一步：命中也会改变访问顺序',15,INK,True)
    u.lines(287,678,['LRU 关注的是“最近一次访问”。命中页面也需要移到最近使用的位置，',
                     '否则下一次缺页时，可能淘汰一个刚刚访问过的页面。'],14,'#60747C',25)
    u.txt(266,791,'本题已记录到「学习证据」',12,MUTED)
    u.button(895,790,168,'下一题    →')
    u.card(238,889,854,102)
    u.pill(260,911,'接下来 · 填空题','#EDF1F5','#5B7486',125)
    u.txt(403,915,'LRU 淘汰的是：',15,INK)
    u.box(552,906,185,38,'#F8FAFA',6,LINE)
    u.txt(568,918,'输入答案',12,'#A2AFB3')
    u.txt(751,915,'的页面。',15,INK)
    u.txt(260,962,'判断、选择、填空与编程实验，共同构成这个知识点的学习记录。',12,MUTED)
    u.card(1114,276,458,351)
    u.icon(1138,301,'book',TEAL,19);u.txt(1168,302,'一分钟概念卡',17,INK,True)
    u.txt(1138,350,'Least Recently Used',13,TEAL,True,True)
    u.lines(1138,386,['淘汰最长时间没有被访问的页面。', '每一次访问，都会重新排列“最近使用”', '的顺序，包括命中。'],16,INK,29)
    u.txt(1138,494,'一次命中之后',11,MUTED)
    for i,(n,fill) in enumerate([('2','#F0F3F4'),('0','#F0F3F4'),('3',MINT)]):
        xx=1140+i*104;u.box(xx,522,72,49,fill,7);u.txt(xx+36,534,n,22,TEAL if n=='3' else INK,True,True,'center')
        if i<2:u.icon(xx+78,535,'arrow','#ADBCC1',18)
    u.txt(1140,590,'最久未用',11,MUTED);u.txt(1420,590,'最近使用',11,TEAL,align='right')
    u.card(1114,647,458,185)
    u.txt(1138,671,'本组进度',16,INK,True);u.txt(1548,674,'已完成 3 / 8',12,MUTED,align='right')
    for i in range(8):
        xx=1138+i*51
        color=GREEN if i<2 else RED if i==2 else '#E9EFF0'
        u.box(xx,714,40,40,color,7)
        u.txt(xx+20,725,str(i+1),14,WHITE if i<3 else MUTED,True,True,'center')
    u.txt(1138,784,'首次作答  ·  答对 2 题  ·  待巩固 1 题',12,MUTED)
    u.box(1114,852,458,139,NAVY,13)
    u.icon(1138,875,'spark','#7DD5BB',19);u.txt(1167,876,'让图谱导师再解释一下',16,WHITE,True)
    u.txt(1138,916,'“能用具体的页面序列举个例子吗？”',14,'#B9CFD6')
    u.txt(1138,957,'结合本题与前置知识提问',12,'#87C9B8');u.icon(1523,953,'arrow','#87C9B8',19)
    return u.save('02-knowledge-practice')


def replacement_trace(sequence, capacity, algorithm):
    frames=[]
    recent=[]
    queue=[]
    steps=[]
    for i,page in enumerate(sequence):
        hit=page in frames
        removed=None
        if not hit:
            if len(frames)==capacity:
                if algorithm=='LRU': removed=recent[0]
                elif algorithm=='FIFO': removed=queue[0]
                else:
                    rest=sequence[i+1:]
                    distance=lambda p: rest.index(p) if p in rest else len(sequence)+1
                    removed=max(frames,key=distance)
                frames[frames.index(removed)]=page
                recent.remove(removed)
                queue.remove(removed)
            else: frames.append(page)
            queue.append(page)
        if page in recent: recent.remove(page)
        recent.append(page)
        steps.append(dict(page=page,hit=hit,frames=frames[:],recent=recent[:],removed=removed))
    return steps


def simulator_screen():
    u=UI('编程实验','03')
    u.heading('页面置换实验室','把抽象算法拆成每一步，看清页面为什么被留下或淘汰。','实验空间  /  算法可视化  /  内存管理')
    u.button(1391,128,181,'进入编程实验    →',False)
    u.card(238,211,1334,102)
    u.txt(262,230,'页面访问序列',11,MUTED)
    u.box(261,252,716,40,'#F8FAFA',7,LINE)
    u.txt(278,263,'7   0   1   2   0   3   0   4   2   3   0   3   2',17,INK,latin=True)
    u.txt(1001,230,'物理块数量',11,MUTED)
    u.box(1001,252,116,40,WHITE,7,LINE)
    u.txt(1015,263,'−    3    +',16,INK,latin=True)
    u.txt(1141,230,'页面置换策略',11,MUTED)
    u.box(1141,252,406,40,'#F2F5F5',8)
    u.box(1279,255,126,34,TEAL,6)
    for xx,label in [(1207,'FIFO'),(1342,'LRU'),(1477,'OPT')]:
        u.txt(xx,263,label,14,WHITE if label=='LRU' else MUTED,label=='LRU',True,'center')
    u.card(238,334,942,448)
    u.txt(263,359,'执行过程',18,INK,True)
    u.txt(1155,363,'第 08 步 / 共 13 步',12,MUTED,align='right')
    seq=[7,0,1,2,0,3,0,4,2,3,0,3,2]
    steps=replacement_trace(seq,3,'LRU')
    current=7
    u.pill(263,399,'访问页面 4','#EFF3F6','#647F90',104)
    u.pill(377,399,'缺页 · 淘汰 2','#FBF0DF','#A27636',119)
    u.txt(263,453,'访问页',12,MUTED)
    cw=56
    start=397
    for j,state in enumerate(steps):
        x=start+j*cw
        if j==current:u.box(x-5,437,49,213,'#F8F0E2',7)
        u.txt(x+19,452,state['page'],17,TEAL if j<current else '#AA7932' if j==current else '#A4B1B6',True,True,'center')
        for row in range(3):
            y=497+row*50
            future=j>current
            value='·' if future else str(state['frames'][row]) if row<len(state['frames']) else '—'
            changed=not future and not state['hit'] and row<len(state['frames']) and state['frames'][row]==state['page']
            fill='#E4F1EB' if changed else '#F2F5F5'
            if j==current and changed:fill='#F0DCB8'
            u.box(x-2,y,42,39,fill,6)
            u.txt(x+19,y+10,value,16,TEAL if changed else INK if not future else '#C4CDD0',True,True,'center')
        if j<=current:
            u.circle(x+19,672,3.5,GREEN if state['hit'] else AMBER)
            u.txt(x+19,690,'命中' if state['hit'] else '缺页',10,MUTED,align='center')
    for row in range(3):u.txt(263,510+row*50,'物理块 '+str(row+1),12,MUTED)
    u.line([(263,728),(1154,728)])
    u.button(263,742,81,'上一帧',False,28)
    u.button(354,742,90,'▶  播放',True,28)
    u.button(454,742,81,'下一帧',False,28)
    u.progress(560,755,433,8/13,TEAL,h=4)
    u.circle(560+433*8/13,757,6,TEAL,WHITE,2)
    u.txt(1032,748,'1.0×    重置',12,MUTED)
    u.card(1202,334,370,448)
    u.pill(1226,355,'LRU 决策过程',MINT,TEAL,114)
    u.txt(1226,401,'为什么淘汰页面 2？',20,INK,True)
    u.lines(1226,443,['页面 4 尚未驻留，三个物理块已满。', '在置换前的内存页面中，页面 2', '距离上一次被访问的时间最久。'],14,'#697C84',26)
    u.txt(1226,544,'置换前的访问顺序',11,MUTED)
    for i,n in enumerate([2,3,0]):
        xx=1226+i*109
        u.box(xx,572,82,55,'#F9EBD6' if i==0 else '#EFF4F3',7)
        u.txt(xx+41,586,n,22,'#A57530' if i==0 else INK,True,True,'center')
        if i<2:u.icon(xx+86,589,'arrow','#A6B6BC',16)
    u.txt(1226,641,'最久未用',11,'#A57530');u.txt(1526,641,'最近使用',11,MUTED,align='right')
    u.line([(1226,677),(1548,677)])
    u.txt(1226,697,'已访问 8 次',12,MUTED)
    u.txt(1226,728,'6 次缺页',20,INK,True);u.txt(1548,735,'缺页率 75%',12,'#A27636',align='right')
    u.card(238,804,555,187)
    u.txt(263,829,'完整序列 · 算法对比',17,INK,True)
    u.txt(768,833,'缺页次数 / 13 次访问',11,MUTED,align='right')
    for i,alg in enumerate(['FIFO','LRU','OPT']):
        faults=sum(not st['hit'] for st in replacement_trace(seq,3,alg))
        yy=873+i*33
        u.txt(264,yy,alg,13,TEAL if alg=='LRU' else MUTED,alg=='LRU',True)
        u.progress(326,yy+6,354,faults/13,TEAL if alg=='LRU' else '#B4C6CC',h=10)
        u.txt(743,yy,faults,13,INK,True,True,'right')
    u.box(813,804,759,187,NAVY,13)
    u.pill(838,827,'预测下一步','#26473F','#98D8BF',97)
    u.txt(953,832,'下一次访问页面 2，应该淘汰谁？',17,WHITE,True)
    for i,n in enumerate([4,0,3]):
        xx=838+i*114;u.box(xx,879,98,46,'#25404B',7,'#4E6975')
        u.txt(xx+49,892,'页面 '+str(n),15,'#D6E6EB',align='center')
    u.button(1319,879,224,'提交预测    →')
    u.txt(838,955,'预测结果会作为 LRU 的一条学习证据。',12,'#9CB7C0')
    return u.save('03-algorithm-lab')


def teacher_screen():
    u=UI('教学概览','04',True)
    u.heading('看见每个知识点的学习进展','把班级整体表现与具体错因连接起来，找到下一次讲解的重点。','教师空间  /  软件工程 2301')
    u.button(1279,124,167,'软件工程 2301',False)
    u.button(1457,124,115,'最近 7 天',False)
    metrics=[('参与学习','32 / 36','本周覆盖 89% 的学生',TEAL),('普通题正确率','76.4%','较上周提升 6.8 个百分点',GREEN),('实验通过率','68.2%','23 名学生完成页面置换实验',TEAL),('待补强知识点','4','优先关注 LRU 与缺页中断',AMBER)]
    for i,(label,val,sub,col) in enumerate(metrics):
        xx=238+i*339
        u.card(xx,212,317,135)
        u.txt(xx+23,232,label,12,MUTED)
        u.txt(xx+23,261,val,29,INK,True,True)
        u.circle(xx+284,278,8,None,col,2)
        u.txt(xx+23,312,sub,11,col)
    u.card(238,370,914,374)
    u.txt(263,394,'班级知识掌握分布',18,INK,True)
    u.txt(1126,400,'展示 6 位学生 · 查看全部 36 人 →',11,TEAL,align='right')
    topics=['页表','TLB','缺页中断','FIFO','LRU','OPT','工作集','进程调度']
    values=[[90,78,71,92,38,0,0,85],[88,81,76,95,84,79,62,87],[82,64,45,76,41,0,0,79],[94,88,82,91,67,70,0,92],[70,58,39,74,34,0,0,69],[86,76,71,89,73,62,54,84]]
    names=['林同学','许同学','周同学','王同学','陈同学','赵同学']
    start=398;cellw=88
    for j,label in enumerate(topics):u.txt(start+j*cellw+35,443,label,11,MUTED,align='center')
    for i,row in enumerate(values):
        yy=471+i*35
        u.txt(265,yy+7,names[i],12,INK)
        for j,v in enumerate(row):
            fill='#EBF0F1' if v==0 else '#F9E1C1' if v<50 else '#EDF1DB' if v<70 else '#BFE0D0' if v<85 else '#7ABCA6'
            col='#A3B1B6' if v==0 else '#855F37' if v<50 else '#52644A' if v<70 else '#285D4B'
            xx=start+j*cellw
            u.box(xx,yy,70,28,fill,4)
            u.txt(xx+35,yy+7,'—' if v==0 else str(v),12,col,latin=True,align='center')
    u.line([(263,696),(1128,696)])
    for i,(label,fill) in enumerate([('未学习','#EBF0F1'),('待补强','#F9E1C1'),('巩固中','#EDF1DB'),('已掌握','#7ABCA6')]):
        xx=267+i*111;u.box(xx,714,11,11,fill,2);u.txt(xx+19,713,label,10,MUTED)
    u.txt(1128,713,'点击数值，查看作答与实验依据',10,MUTED,align='right')
    u.card(1174,370,398,374)
    u.txt(1198,395,'值得关注的知识点',18,INK,True)
    u.txt(1198,432,'按薄弱学生占比排序',12,MUTED)
    for i,(name,ratio,count) in enumerate([('LRU 页面置换',.44,'14 人'),('缺页中断',.31,'10 人'),('TLB 命中与缺失',.22,'7 人')]):
        yy=475+i*76
        u.txt(1199,yy,name,14,INK,True)
        u.txt(1548,yy,count+'需巩固',11,MUTED,align='right')
        u.progress(1199,yy+33,349,ratio,AMBER if i==0 else '#9BBCAE',h=7)
    u.txt(1198,717,'为班级布置针对性练习  →',12,TEAL)
    u.card(238,766,561,225)
    u.txt(263,792,'最近学习动态',17,INK,True)
    u.txt(774,796,'查看成长时间轴 →',11,TEAL,align='right')
    for i,(name,act,sub,col) in enumerate([('林同学','完成 LRU 判断题','发现误区：命中后未更新访问顺序',AMBER),('许同学','通过页面置换编程实验','实践应用 72 → 84 · 已记录新证据',GREEN),('周同学','完成缺页中断巩固练习','首次独立答对 3 题',TEAL)]):
        yy=840+i*46
        u.circle(270,yy+7,4,col)
        if i<2:u.line([(270,yy+14),(270,yy+39)],LINE,1)
        u.txt(290,yy,name+'  '+act,13,INK)
        u.txt(290,yy+23,sub,10,MUTED)
        u.txt(772,yy,'刚刚' if i==0 else str(i*4)+' 分钟前',10,MUTED,align='right')
    u.box(821,766,751,225,NAVY,13)
    u.pill(845,788,'教学建议','#26473F','#9AD8C1',82)
    u.txt(945,792,'下次课，可以从这个误区讲起',18,WHITE,True)
    u.lines(845,841,['14 名学生在 LRU 的“页面命中后更新顺序”上仍需巩固。',
                     '建议结合页面访问序列演示，再布置一组判断与填空题。'],15,'#BBD0D8',28)
    u.txt(845,940,'依据：本周 48 次相关作答与 12 次实验提交',11,'#8EAFBA')
    u.button(1379,925,164,'生成巩固练习    →')
    return u.save('04-teaching-insights')


def overview(images):
    width,height=2400,1770
    sheet=Image.new('RGB',(width,height),'#EAF0F1')
    d=ImageDraw.Draw(sheet)
    def text(x,y,t,size=20,color=INK,bold=False):
        d.text((x,y),t,font=ImageFont.truetype(str(FONT_DIR/('msyhbd.ttc' if bold else 'msyh.ttc')),size),fill=color,anchor='lt')
    text(58,36,'OSLab',37,INK,True)
    text(223,45,'知识图谱驱动的学习工作台',25,INK,True)
    text(2340,47,'',14)
    text(59,96,'视觉方向 A  /  浅色工作台 · 深色图谱 · 青绿强调色',19,MUTED)
    titles=['01  知识图谱 · 发现薄弱点','02  知识点练习 · 概念与普通题','03  算法实验室 · 看见执行过程','04  教师看板 · 从班级到个人']
    for i,img in enumerate(images):
        col=i%2;row=i//2
        x=54+col*1170;y=146+row*802
        text(x,y,titles[i],23,INK,True)
        small=img.resize((1136,738),Image.Resampling.LANCZOS)
        sheet.paste(small,(x,y+43))
    sheet.save(OUT/'00-overview.png')
    sheet.resize((1600,1180),Image.Resampling.LANCZOS).save(OUT/'00-overview-preview.png')


if __name__=='__main__':
    images=[graph_screen(),practice_screen(),simulator_screen(),teacher_screen()]
    overview(images)
    print('Rendered 4 screens at 3200 x 2080 and a 2400 x 1770 overview.')
    print(OUT)
