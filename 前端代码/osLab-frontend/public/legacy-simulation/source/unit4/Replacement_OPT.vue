<template>
    <div id="outer">
    
    <el-card class="box-card">
    <div class="setting">
 <el-scrollbar class="scrollbar">
      <div class="box-card-setting-title">
        <div class="box-card-setting-title-1">页面置换算法</div>
        <div class="box-card-setting-title-2">最佳(OPT)置换算法</div> 
    </div>
    <label>内存块数量：</label>
     <el-input-number size="mini" v-model="phy_num" @change="handleChange" :min="1" :max="60" ></el-input-number><br>
<el-divider />
   <label>访问顺序：</label>
    <el-form ref="form1" :inline="true" :model="page" >
    <div v-for="(item, index) in page.page_input" :key="index">
        <el-form-item label-width="50px">时刻{{index}}</el-form-item>
        <el-form-item label-width="120px" label="页号" :prop="'page_input.' + index + '.no'"
        :rules="[{required: true, message: '页号不能为空', trigger: 'blur'},]">
        <el-input-number size="mini" v-model="item.no" @change="handleChange" :min="0" :max="100" ></el-input-number>
        </el-form-item>
        <el-form-item>
        <el-button size="mini" v-if="index+1 == page.page_input.length" @click="addItem" type="primary">增加</el-button>
        <el-button size="mini" v-if="index !== 0" @click="deleteItem(item, index)" type="danger">删除</el-button>
        </el-form-item>
    </div>
    </el-form>
    </el-scrollbar>
    </div>
    <el-divider />
    <div id="option">
       
        <el-button size="mini"  @click="onSubmit1()" type="primary">提交</el-button>
        <el-button size="mini"  @click="reset()" type="danger">重置</el-button>
        <el-button size="mini"  v-on:click="show = !show" type="primary">{{this.GLOBAL.tips}}</el-button>
        <el-button size="mini" @click="drawer = true">{{this.GLOBAL.analysis}}</el-button>
       <div class="option-play">
            <div class="option-speed">当前倍速：{{playbackRate}}</div>
            <el-button size="mini"   @click="speedDown()" id="speedDown" class="el-icon-minus" :disabled = "playbackRate==0.1">{{this.GLOBAL.speedDown}}</el-button>
            <el-button size="mini"   @click="speedUp()" id="speedUp"  class="el-icon-plus">{{this.GLOBAL.speedUp}}</el-button>
            <el-button size="mini"   @click="stop()" id="pause">{{this.GLOBAL.stop}}</el-button> 
            <el-button size="mini"   @click="oneSpeed()" >{{this.GLOBAL.oneSpeed}}</el-button>
            <el-button size="mini"   @click="halfSpeed()" >{{this.GLOBAL.halfSpeed}}</el-button>
            <el-button size="mini"   @click="doubleSpeed()" >{{this.GLOBAL.doubleSpeed}}</el-button>
        </div>
    </div>
    </el-card>
  
     <el-divider />
    
  <!--<div id="canvas" style="width: 1200px; height: 500px; background: #dff0df; position:relative">
    
     <transition name="fade">
        <div id="tips" v-if="show" v-html="tips_text">{{tips_text}}无法识别换行符
        </div>
    </transition>
    
    <div id="adaptiveR"></div>
    </div>-->
      <div id="canvas" style="width: 1200px; height: 500px;  position:relative">

  
   <div id="adaptive" style="width: 850px; height: 500px; background: #fff; position:absolute;margin-left: 75px;"></div>
  
    <transition name="fade">
    <div id="tips14" v-if="show" v-html="tips_text">
    </div>
    </transition>

    </div>
   


    
    <el-drawer
    title="最佳置换算法"
    :visible.sync="drawer"
    :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">最佳置换算法</div>
            <div id="drawer-text-content">     
                <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px; line-height: 1.5;">选择以后永不使用的页面，或是在最长时间内不再被访问的页面，以保证获得最低的缺页率。</div>
            
             

                <span style="font-weight:bold; margin-bottom:7px; display: block;">特点：</span>
                <div style="margin:10px;margin-bottom: 20px; line-height: 1.5;">缺页率最低，但由于人们目前无法预知进程在内存下的若干页面中哪个是未来最长时间内不再被访问的，因而该算法无法实现。</div>

                


            </div>
        
        </div>
    </el-drawer>
    <el-divider /> 
    </div>
</template>
<script>

import navbar from '@/components/Navbar.vue'
import * as spritejs from 'spritejs';
const { Scene, Sprite, Rect, Label, Group, Path} = spritejs;
const PAGE_NEW='red'//内存中新置换的页面
const PAGE_RIGHT='green'//内存中命中的页面

const LINEY='liney'
const LINEX='linex'
const TABLE_SIZE=[1100,700]
export default {
    name: "PageOPT",

    data() {
  return {      
    page: {
      page_input:[{
          no:4
      },{
          no:3
      },{
          no:2
      },{
          no:1
      },{
     no:4
      },{
          no:3
      },{
          no:5
      },{
          no:4
      },{
          no:3
      },{
          no:2
      },{
          no:1
      },
      {
          no:5
      }
      ]
    },
    page_line:[],
    phy_line:[],
    time_now:0,//记录延后执行的时间
    layer:"",//画布
    page_num_count:4,
    phy_num:3,//内存块数目
    replace_num:0,//置换次数
    lacking_num:0,//缺页次数
    // point:0,//要进行页面置换的内存块号
    p1_last_posX:95,//红色圆圈指示符的x位置
    drawer: false,
    broken_times:0,//中断次数
    tips_text:"",//提示文字
    show:false,

    pause:false,
    playbackRate:1.0,
  }
},
methods:{
    addItem() {
        var len=this.page.page_input.length;
        this.page.page_input.push({
        no:"",
        });
    },
    deleteItem(item, index) {
        this.page.page_input.splice(index, 1);
        console.log(this.page.page_input, "删除");
    },
     speedDown(){
        this.layer.timeline.playbackRate -= 0.1
        this.playbackRate=this.layer.timeline.playbackRate.toFixed(1);
    },
    speedUp(){
        this.layer.timeline.playbackRate += 0.1;
        this.playbackRate=this.layer.timeline.playbackRate.toFixed(1);
    },
    halfSpeed(){
        this.layer.timeline.playbackRate = 0.5;
        this.playbackRate=this.layer.timeline.playbackRate;
    },
    oneSpeed(){
        this.layer.timeline.playbackRate = 1;
        this.playbackRate=this.layer.timeline.playbackRate;
    },
    doubleSpeed(){
        this.layer.timeline.playbackRate = 2.0;
        this.playbackRate=this.layer.timeline.playbackRate;
    },
    stop(){
            if(this.pause == false) this.layer.timeline.playbackRate = 0;
            else {
                this.layer.timeline.playbackRate = this.playbackRate;
            }
            this.pause = !this.pause;
    },
    init(){
        const container = document.getElementById("adaptive");
        const scene = new Scene({ // 初始化场景scene，它不是canvas，只是一个用来包裹canvas的div元素
            container,
            width: 860,
            height: 500,
            mode: 'stickyWidth',
        });        
        const layer = scene.layer( 'test',{});// 创建背景图层layer，这个就是真正意义上的canvas了
        this.layer=layer;
       
    },
    drawLineY(phy_num,page_num){//this.phy_num,this.page_num
        page_num+=1;
        const arrowGroup=new Group();
        arrowGroup.attr({
            name:LINEY,
            size: TABLE_SIZE,
            pos: [50,50],
            // bgcolor: '#cec',
        });
        while(page_num>=0){
            var num=300+100*phy_num;
            const arrow = new Path();
            arrow.attr({
                d: 'md0,d0l d0,d'+num,
                scale:0.5,
                strokeColor: 'black',
                lineWidth: 5,
                pos:[page_num*50+20,20],
                zIndex:100,
                opacity:0,
            });
            arrowGroup.appendChild(arrow);
            page_num--;
        }
        this.layer.append(arrowGroup)
        for(var i=0;i<arrowGroup.children.length;i++){
            arrowGroup.children[i].animate([
                {opacity:0},
                {opacity:1},
            ], {
                delay:this.time_now,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
        }
    },
    drawLineX(phy_num,page_num){
        phy_num+=3;
        const arrowGroup=new Group();
        arrowGroup.attr({
            name:LINEX,
            size: TABLE_SIZE,
            pos: [50,50],
            // bgcolor: '#cec',
        });
        while(phy_num>=0){
            var num=100+100*page_num
            const arrow = new Path();
            arrow.attr({
                // name:LINEY,
                d: 'md0,d0l d'+num+',d0',
                scale:0.5,
                strokeColor: 'black',
                lineWidth: 5,
                pos:[20,phy_num*50+20],
                zIndex:100,
                opacity:0,
            });
            arrowGroup.appendChild(arrow);
            phy_num--;
        }
        this.layer.append(arrowGroup)
        for(var i=0;i<arrowGroup.children.length;i++){
            arrowGroup.children[i].animate([
                {opacity:0},
                {opacity:1},
            ], {
                delay:this.time_now,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
        }
    },
    drawTitile(x,y){
        const titleGroup=new Group();
        titleGroup.attr({
            name:"titleGroup",
            size: TABLE_SIZE,
            pos: [50,50],
        });
        
        const title1 = new Label("时刻");
         title1.attr({
                name:"title1",
                pos: [45, 45],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
                opacity:0,
            });
        const title2 = new Label("页面");
         title2.attr({
                name:"title2",
                pos: [45, 45+50],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
                opacity:0,
            });
        titleGroup.appendChild(title1)
        titleGroup.appendChild(title2)
        var m=1;
        while(m<=x){
            const title = new Label("块"+m);
            title.attr({
                name:"title",
                pos: [45, 95+m*50],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
                opacity:0,
            });
            m++;
            titleGroup.appendChild(title)
        }
        const title3 = new Label("缺页");
        title3.attr({
            name:"title3",
            pos: [45, 145+50*x],
            anchor: [0.5, 0.5],
            fillColor: this.GLOBAL.COLOR_FONT,
            font: '20px "黑体"',
            zIndex:100,
            opacity:0,
        });
       titleGroup.appendChild(title3);
        var n=0;
        while(n<y){
            // console.log(this.page.page_input[n].no.toString())
            const time = new Label(n.toString());
            time.attr({
                name:"time",
                pos: [95+n*50, 45],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:200,
                opacity:0,
            });
            const title = new Label(this.page.page_input[n].no.toString());
            title.attr({
                name:"title",
                pos: [95+n*50, 95],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:200,
                opacity:0,
            });
            n++;
            titleGroup.appendChild(time)
            titleGroup.appendChild(title)
        }
       this.layer.append(titleGroup)
       for(var i=0;i<titleGroup.children.length;i++){
            titleGroup.children[i].animate([
                {opacity:0},
                {opacity:1},
            ], {
                delay:this.time_now,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
        }
    },
    drawPage(num,p1){//num:page_line下标i  p1:红色圆圈
        p1.animate([
                {opacity:0,x:this.p1_last_posX},
                {opacity:1,x:145+50*num},
            ], {
                delay:this.time_now,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
        });
        this.time_now+=500;
        this.p1_last_posX=145+50*num;
        //新进入的，红色放大示意
        for(var i=0;i<this.phy_num;i++){
            if(this.phy_line[i].no != -1){
                const page = new Label(this.phy_line[i].no.toString())
                page.attr({
                    name:"page",
                    pos: [145+50*num, 145+50+50*i],
                    anchor: [0.5, 0.5],
                    fillColor: this.GLOBAL.COLOR_FONT,
                    opacity:0,
                    font: '20px "黑体"',
                    zIndex:100,
                });
                this.page_line[num].pageGroup=page;//存入页号动画元素
                this.layer.append(this.page_line[num].pageGroup);
                this.page_line[num].pageGroup.animate([
                    {opacity:0},
                    {opacity:1},
                ], {
                  delay:this.time_now,
                  duration: 500,
                  iterations:1,
                  fill: 'forwards',
                });
                this.time_now+=500;
                //强调动画
                if(this.phy_line[i].new == true){
                    var phy_no=i+1
                    var page_no=this.page_line[num].no
                    this.layer.timeline.setTimeout(() => {
                        //this.tips_text+="时刻"+num.toString()+",块【"+phy_no+"】加入"+page_no+"号页"+'<br/>'

                        this.tips_text+="时刻"+num.toString()+","+page_no+"号页加入到"+"块【"+phy_no+"】中"+'<br/>'
                        console.log("this.page_line[num].no"+this.page_line[num].no)
                    },this.time_now);
                    this.page_line[num].pageGroup.animate([
                        {fillColor:this.GLOBAL.COLOR_FONT,font:'20px "黑体"'},
                        {fillColor:PAGE_NEW,font:'30px "黑体"'},
                        {fillColor:this.GLOBAL.COLOR_FONT,font:'20px "黑体"'},
                    ], {
                        delay:this.time_now,
                        duration: 1000,
                        iterations:1,
                        fill: 'forwards',
                    });
                    this.time_now+=1000;
                    this.phy_line[i].new=false;
                    //缺页部分打钩
                    this.broken_times++;
                    const p2 = new Path();
                    p2.attr({
                        d: 'm d0,d0l d20,d20l d30,d-60',
                        scale: 0.5,
                        strokeColor: 'red',
                        opacity:0,
                        lineWidth: 5,
                        pos: [130+50*num, 195+this.phy_num*50],
                    });    
                    this.layer.append(p2);
                    p2.animate([
                        {opacity:0},
                        {opacity:1},
                    ], {
                        delay:this.time_now,
                        duration: 500,
                        iterations: 1,
                        fill: 'forwards',
                    });
                    this.time_now+=500;
                }
                if(this.phy_line[i].right == true){
                    var phy_no=i+1
                    var page_no=this.page_line[num].no
                    this.layer.timeline.setTimeout(() => {
                        this.tips_text+="时刻"+num.toString()+",命中块【"+phy_no+"】中的"+page_no+"号页"+'<br/>'
                        // console.log("this.page_line[num].no"+this.page_line[num].no)
                    },this.time_now);
                    this.page_line[num].pageGroup.animate([
                    {fillColor:this.GLOBAL.COLOR_FONT,font:'20px "黑体"'},
                    {fillColor:PAGE_RIGHT,font:'30px "黑体"'},
                    {fillColor:this.GLOBAL.COLOR_FONT,font:'20px "黑体"'},
                    ], {
                        delay:this.time_now,
                        duration: 1000,
                        iterations:1,
                        fill: 'forwards',
                    });
                    this.time_now+=1000;
                    this.phy_line[i].right=false;
                }
            }  
        }
    },
    replace(num,max,prenum){
         //文字提示
        this.layer.timeline.setTimeout(() => {
            var phy_no=max+1
            var page_no=this.page_line[num].no
           this.tips_text+="*时刻"+num+",块【"+phy_no+"】中的"+prenum+"号页未来长时间不再使用，将其调出内存"+'<br/>'
        },this.time_now);

        const p1 = new Path();
        p1.attr({
            d: 'M-50,0a40,40 0 1 1 100,0a40,40 0 1 1 -100,0',
            scale: 0.5,
            strokeColor: 'red',
            opacity:0,
            lineWidth: 5,
            pos: [145+50*num, 145],
        });    
        this.layer.append(p1);
        p1.animate([//将箭头透明度设置为0，达到隐形的效果
                {opacity:0,y: 145},
                {opacity:1,y: 195+max*50},
            ], {
                delay:this.time_now,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
        this.time_now+=500;
        //找到要替换的位置
        var temp=[145+50*num,195+max*50]
        // console.log(temp)
        for(var j=this.layer.children.length-1;j>3;j--){
            var temp2=this.layer.children[j].attributes.pos
            if(temp2[0]==temp[0] && temp2[1] == temp[1]){
                this.layer.children[j].animate([
                    {text: ""},
                    {text:this.page_line[num].no.toString(),fillColor:this.GLOBAL.COLOR_FONT_CHANGE,},
                ], {
                    delay:this.time_now,
                    duration: 500,
                    iterations: 1,
                    fill: 'forwards',
                });
                this.time_now+=500;
                //缺页部分打钩
                this.broken_times++;
                const p2 = new Path();
                p2.attr({
                    d: 'm d0,d0l d20,d20l d30,d-60',
                    scale: 0.5,
                    strokeColor: 'red',
                    opacity:0,
                    lineWidth: 5,
                    pos: [130+50*num, 195+this.phy_num*50],
                });    
                this.layer.append(p2);
                p2.animate([
                    {opacity:0},
                    {opacity:1},
                ], {
                    delay:this.time_now,
                    duration: 500,
                    iterations: 1,
                    fill: 'forwards',
                });
                this.time_now+=500;
                break;
            }
        }
        
        this.layer.timeline.setTimeout(() => {
            p1.remove()
        },this.time_now+500);
        this.time_now+=500;
    },
    searchFuture(now,j){
        var i=now+1;
        var page_in_phy=this.phy_line[j].no;//内存块j中的页号
        for(i;i<this.page_line.length;i++){
            if(this.page_line[i].no==page_in_phy){
                return i;//返回下一次到达的时间
            }
        }
        return 1000;//没有下一次
    },
    OPT(){
        const p1 = new Path();
        p1.attr({
            d: 'M-50,0a40,40 0 1 1 100,0a40,40 0 1 1 -100,0',
            scale: 0.5,
            strokeColor: 'red',
            opacity:0,
            lineWidth: 5,
            pos: [this.p1_last_posX, 145],
        });    
        this.layer.append(p1);
        var judge=-1;
        var future=[];
        for(var i =0;i<this.page_line.length;i++){
            for(var j=0;j<this.phy_num;j++){
                if(this.phy_line[j].no==this.page_line[i].no){//一旦在内存之内，直接输出 
                    future[j]=this.searchFuture(i,j);//更新
                    this.phy_line[j].right=true;
                    judge=1;
                    break; 
                }
                if(this.phy_line[j].no==-1){//不在内存之中并且没有满 ，也直接输出 
                    this.phy_line[j].no=this.page_line[i].no;
                    this.phy_line[j].new=true;
                    this.lacking_num++; 
                    future[j]=this.searchFuture(i,j);//更新
                    judge=1;
                    break; 
                }
            }
            this.drawPage(i,p1);
            if(judge==-1){ 
                var max=0;
                for (var k=0;k<this.phy_num;k++)
                {
                    if(future[k]>future[max])
                        max=k;
                }
                var prenum = this.phy_line[max].no;
                this.phy_line[max].no=this.page_line[i].no;
                future[max] = this.searchFuture(i,max);//更新
                this.replace(i,max,prenum);
                this.replace_num++;
                this.lacking_num++;
                }
            judge=-1; 
        }
    },
    showEnd(){//显示缺页中断次数和缺页率
        const broken_text = "<br/>缺页中断次数："+this.broken_times.toString()
       
        var num=((this.broken_times/this.page_line.length)*100).toFixed(2);
        this.layer.timeline.setTimeout(() => {
           this.tips_text+=broken_text+'<br/>'
           this.tips_text+="缺页率："+num+"%"+'<br/>'
        },this.time_now);
        console.log('演示完毕')
    },
    pre_startAnimate(phy_num,page_line_len){
        const lineGroup=new Group();
        lineGroup.attr({
            size: [110,phy_num*50+10],
            pos: [100,100],
        });
        var i=phy_num;
        var preLineXs=[]
        while(i>=0){
            const preLineX = new Path();
            preLineX.attr({
                // name:LINEY,
                d: 'md0,d0l d100,d0',
                scale:1,
                strokeColor: 'blue',
                lineWidth: 5,
                pos:[100,i*50+100],
                zIndex:100,
            });
            
            i--;
            lineGroup.appendChild(preLineX)
            preLineXs.push(preLineX)
            // this.layer.append(preLineX)
        }
        var num=phy_num*50
        const preLineY1 = new Path();
        preLineY1.attr({
            // name:LINEY,
            d: 'md0,d0l d0,d'+num,
            scale:1,
            strokeColor: 'blue',
            lineWidth: 5,
            pos:[100,100],
            zIndex:100,
        });
        const preLineY2 = new Path();
        preLineY2.attr({
            d: 'md0,d0l d0,d'+num,
            scale:1,
            strokeColor: 'blue',
            lineWidth: 5,
            pos:[200,100],
            zIndex:100,
        });
        lineGroup.appendChild(preLineY1)
        lineGroup.appendChild(preLineY2)

        var texts=[]
        for(var j=0;j<phy_num;j++){
            const title1 = new Label("内存块"+(j+1));
            title1.attr({
                name:"title1",
                pos: [150,j*50+125],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
            });
            lineGroup.appendChild(title1)
            texts.push(title1)
        }
        this.layer.append(lineGroup)
        lineGroup.animate([
                {x:100,y:100},
                {x:-30,y:70},
            ], {
                delay:this.time_now+2000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
        });
        
         for(var j=0;j<phy_num;j++){
            texts[j].animate([
                 {opacity:1},
                {opacity:0},
                
            ], {
                delay:this.time_now+2000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
        }

        for(var j=0;j<=phy_num;j++){
         preLineXs[j].animate([
                {d: 'md0,d0l d100,d0',},
                {d: 'md0,d0l d50,d0',},
            ], {
                delay:this.time_now+2000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
        }
      
        preLineY2.animate([
            {x:200},
            {x:150},
        ], {
            delay:this.time_now+2000,
            duration: 500,
            iterations: 1,
            fill: 'forwards',
        });

         this.time_now+=2000;
        
        for(var j=0;j<=phy_num;j++){
         preLineXs[j].animate([
                {opacity:1},
                {opacity:0},
            ], {
                delay:this.time_now+1000,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
            });
        }
        preLineY1.animate([
            {opacity:1},
            {opacity:0},
        ], {
            delay:this.time_now+1000,
            duration: 1000,
            iterations: 1,
            fill: 'forwards',
        });
        preLineY2.animate([
            {opacity:1},
            {opacity:0},
        ], {
            delay:this.time_now+1000,
            duration: 1000,
            iterations: 1,
            fill: 'forwards',
        });
        this.time_now+=1000;
    },
    async onSubmit1(){
        this.page_line=this.page.page_input.concat([]);
        this.pre_startAnimate(this.phy_num,this.page_line.length);
        this.drawLineY(this.phy_num,this.page_line.length);
        this.drawLineX(this.phy_num,this.page_line.length);
        this.drawTitile(this.phy_num,this.page_line.length);
        for(var i=0;i<this.phy_num;i++){
            const phy_item={
                no:-1,
                new:false,
                right:false,
            }
            this.phy_line.push(phy_item);
        }
        this.OPT();
        this.showEnd();
    },
    reset(){
         this.replace_num=0,//置换次数
    this.lacking_num= 0,//缺页次数 
    this.time_now=0,//记录延后执行的时间
    this.point=0,//要进行页面置换的内存块号
    this.p1_last_posX=145,//红色圆圈指示符的x位置
    this.drawer=false,
    this.broken_times=0,//中断次数
    this.tips_text="",//提示文字
    this.page.page_input = [{ no: "" }];
    this.page_line=[],
    this.phy_line=[],
    this.phy_num = 1;
    this.layer.removeAllChildren();
    },
    handleChange(value) {
        console.log(value);
    },
    },
    mounted() {
    this.init();
    },
}
</script>