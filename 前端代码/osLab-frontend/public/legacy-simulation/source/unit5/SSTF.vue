<template>
<div id="outer">
        <div id="content">
     <el-card class="box-card">
    <div class="setting">
  <el-scrollbar class="scrollbar">
    <div class="box-card-setting-title">
        <div class="box-card-setting-title-1">磁盘调度算法</div>
        <div class="box-card-setting-title-2">最短寻道时间优先(SSTF)算法</div> 
    </div>
    <div class="box-card-setting-head">
        <label>磁头初始位置：</label>
        <el-input-number size="mini" v-model="begin_no" @change="handleChange" :min="1" :max="200" ></el-input-number>
    </div>

    <el-divider />
    
    <label>后续访问磁道：</label>
    <el-form ref="form1" :inline="true" :model="partition" >
    <div v-for="(item, index) in partition.partition_input" :key="index">
        <el-form-item label-width="50px">{{index}}</el-form-item>

        <el-form-item label-width="120px" label="编号" :prop="'partition_input.' + index + '.no'"
        :rules="[{required: true, message: '编号不能为空', trigger: 'blur'},]">
        <el-input-number size="mini" v-model="item.no" @change="handleChange" :min="1" :max="200" ></el-input-number>
        </el-form-item>
        <el-form-item>
               <el-button size="mini" v-if="index+1 == partition.partition_input.length" @click="addItem1" type="primary">增加</el-button>
        <el-button size="mini" v-if="index !== 0" @click="deleteItem1(item, index)" type="danger">删除</el-button>
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

    
   
     <el-drawer
      title="最短寻找时间优先算法"
      :visible.sync="drawer"
      :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">最短寻找时间优先算法</div>
            <div id="drawer-text-content"> 
                <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">选择与当前磁头所在磁道距离最近的磁道，以便使每次的寻找时间最短。</div>
                
                <span style="font-weight:bold; display: block;">特点：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">每次选择最小寻找时间并不能保证平均寻找时间最小，但能提供比FCFS算法更好的性能，这种算法会产生“饥饿现象”。</div>
                
            </div>
            
        </div>
    </el-drawer>

  <el-divider />
    <div id="adaptive_5"></div>
 <transition name="fade">
        <div id="tips5" v-if="show" v-html="tips_text">
        </div>
    </transition>

  <el-divider />
    </div>
</div>
</template>

<script>
import * as spritejs from 'spritejs';
const { Scene, Sprite, Rect, Label, Group, Path} = spritejs;
export default {
    name: 'SSTF',
    data() {
  return {      
    partition: {
      partition_input:[{
        no:130,
      },
      {
        no:100,
      },
      {
        no:20,
      },
      {
        no:60,
      },
      {
        no:90,
      },
       {
        no:140,
      },
      ]
    },
    begin_no:80,//起始位置
    now_index:-1,//当前坐标轴
    partition_line:[],
    axis_pos:0,//下标位置
    axis_line:[],//保存坐标动画元素
    now_partition_pos:100,//记录当前箭头起始位置
    all_move_length:0,//总移动距离
    moveDistance:[],//记录移动距离的数组

    total:0,//访问的磁道数
    partitionSort_line:[],//最短寻道算法后的排序
    time_now:0,//记录延后执行的时间
    layer:"",//画布

    y:30,//高度差

    drawer: false,
    pause:false,
    playbackRate:1.0,

    show:false,
    tips_text:"",//解说文字
    stepArrow:'',//箭头动画元素
    step_line:[]//记录坐标的实际位置
  }
},
methods:{
        addItem1() {
          var len=this.partition.partition_input.length;
          this.partition.partition_input.push({
            no:"",
          });
        },
        deleteItem1(item, index) {
            this.partition.partition_input.splice(index, 1);
            console.log(this.partition.partition_input, "删除");
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
        // *****************************************************************************************************************************
        init(){
          const container = document.getElementById("adaptive_5");
          const scene = new Scene({ // 初始化场景scene，它不是canvas，只是一个用来包裹canvas的div元素
              container,
              width: 2500,
              height: 500,
              mode: 'stickyWidth',
          });        
          const layer = scene.layer( 'test',{});// 创建背景图层layer，这个就是真正意义上的canvas了
          this.layer=layer;
          // const arrow = new Path();
          // arrow.attr({
          //   name:"arrow",
          //   d: 'm d0,d0l d2000,d0',
          //   scale:1,
          //   strokeColor: 'black',
          //   lineWidth:3,
          //   pos: [50,440],
          //   zIndex:100,
          // });
          // this.layer.append(arrow)
        },
        drawAxis(sort_line){//绘制坐标：增加判断，当两个数相邻时，自动拉开一段距离
         const arrow = new Path();
          arrow.attr({
            name:"arrow",
            d: 'm d0,d0l d1,d0',//d: 'm d0,d0l d2000,d0',
            scale:1,
            strokeColor: 'black',
            lineWidth:3,
            pos: [50,440],
            zIndex:100,
          });
          this.layer.append(arrow)
         
          var add_num = 1000/sort_line.length;
          for(var i=0;i<sort_line.length;i++){
            const text = new Label(sort_line[i].no.toString());
            text.attr({
              name:sort_line[i].no.toString(),
              pos: [50+10*sort_line[i].no,450],
              anchor: [0.5, 0.5],
              fillColor: this.GLOBAL.COLOR_FONT,
              font: '20px "黑体"',
              zIndex:100,
              opacity:0,
            });
            
            this.axis_line.push(text);
            this.layer.append(this.axis_line[i]);
            this.axis_pos+=add_num;
          }
          arrow.animate([
                {d: 'm d0,d0l d1,d0'},
                {d: 'm d0,d0l d2000,d0'},
            ], {
                delay:this.time_now,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
          });
           for(var i=0;i<this.axis_line.length;i++){
              this.axis_line[i].animate([
                {opacity: 0},
                {opacity: 1},
            ], {
                delay:this.time_now,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
          });
            console.log("加入")
           }
          this.time_now+=1000;
        },
        drawArrow1(len,start,no,last,pos){//绘制箭头
          this.layer.timeline.setTimeout(() => {
               this.tips_text += '('+(pos+1)+')' ;
                this.tips_text+="磁头移动向内,从"+last+"号移动到"+no.toString()+"号磁道"+'<br/>'
            },this.time_now);
        len=len*2;
            const arrow = new Path();
            arrow.attr({
              name:"arrow",
              d: 'md0,d0l d'+len+',d0l d-35,d-20m d35,d20l d-35,d20m d35,d20 ',
              scale:0.5,
              strokeColor: 'red',
              lineWidth: 5,
              pos: [start,450-this.y],
              zIndex:100,
              opacity:0,
            });
            this.layer.append(arrow)
            arrow.animate([//将箭头透明度设置为0，达到隐形的效果
                {opacity:0},
                {opacity:1},
            ], {
                delay:this.time_now+1000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
            this.time_now+=1000;
            this.y+=30;
            
        },
        drawArrow0(len,start,no,last,pos){//绘制箭头
          this.layer.timeline.setTimeout(() => {
             this.tips_text += '('+(pos+1)+')' ;
               this.tips_text+="磁头移动向外,从"+last+"号移动到"+no.toString()+"号磁道"+'<br/>'
            },this.time_now);
          len=len*2;
            const arrow = new Path();
            arrow.attr({
              name:"arrow",
              d: 'md0,d0l d'+len+',d0l d35,d-20m d-35,d20l d35,d20m d-35,d20 ',
              scale:0.5,
              strokeColor: 'blue',
              lineWidth: 5,
              pos: [start,450-this.y],
              zIndex:100,
              opacity:0,
            });
            
            this.layer.append(arrow)
            arrow.animate([//将箭头透明度设置为0，达到隐形的效果
                {opacity:0},
                {opacity:1},
            ], {
                delay:this.time_now+1000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
            this.time_now+=1000;
            this.y+=30;

        },
      
        //this.now_index记录下标
        SSTF(){
            if(this.now_index == -1){
                //根据初始地址找this.now_index
                var minLen=999;//最小距离
                var nextno=999;
                for(var i=0;i<this.partition_line.length;i++){//查找最短距离
                    var no_next=parseInt(this.partition_line[i].no);
                    var no_now=parseInt(this.begin_no)
                    if(minLen>Math.abs(no_now-no_next)){
                        minLen=Math.abs(no_now-no_next);
                        nextno=i;
                    }
                };
               
            }else{
                this.partition_line[this.now_index].isPass=1;
                this.total++;
                this.partitionSort_line.push(this.partition_line[this.now_index])
                if(this.total==this.partition_line.length){ 
                    console.log(this.partitionSort_line);
                    return;
                }else{
                    //找距离now_x最近的
                    var minLen=999;//最小距离
                    var nextno=999;
                    for(var i=0;i<this.partition_line.length;i++){//查找最短距离
                        if(this.partition_line[i].isPass != 1){
                            var no_next=parseInt(this.partition_line[i].no);
                            var no_now=parseInt(this.partition_line[this.now_index].no)
                            if(minLen>Math.abs(no_now-no_next)){
                                minLen=Math.abs(no_now-no_next);
                                nextno=i;
                            }
                        }
                    };
                }
            }
            if(nextno==999){
                console.log('没了')
            }else{
                this.now_index=nextno;
                this.SSTF();
            }
        },
        FCFS(){
            //找到起始的x轴坐标
            for(var i=0;i<this.axis_line.length;i++){
                if(this.begin_no==this.axis_line[i].attributes.name){
                    this.now_partition_pos=this.axis_line[i].attributes.pos
                    this.step_line.push(this.now_partition_pos[0])
                }
            }
            //依次录入各个坐标的距离和方向
            for(var i=0;i<this.partitionSort_line.length;i++){
                var pos_now=parseInt(this.partitionSort_line[i].no);
                for(var j=0;j<this.axis_line.length;j++){
                    var pos_next=parseInt(this.axis_line[j].attributes.name);//记录
                    if(pos_next == pos_now){//判断是不是next坐标动画元素
                        this.partitionSort_line[i].start= this.now_partition_pos[0];
                        this.partitionSort_line[i].size = this.axis_line[j].attributes.pos[0] - this.now_partition_pos[0];
                        this.all_move_length+=(Math.abs(pos_next -this.now_no));//计算总移动长度
                        this.now_partition_pos = this.axis_line[j].attributes.pos;
                        this.step_line.push(this.now_partition_pos[0])
                        this.now_no=pos_now;
                        break;
                    }
                }
            }
        },
         drawArrowStep(start){
            const arrow = new Path();
            arrow.attr({
              name:"arrow",
              d: 'md0,d0l d0,d-80l d-15,d25m d15,d-25l d15,d25 ',
              scale:0.5,
              strokeColor: 'green',
              lineWidth: 5,
              pos: [start,500],
              zIndex:100,
              opacity:0,
            });
            this.stepArrow=arrow;
            this.layer.append(this.stepArrow)
            this.stepArrow.animate([//将箭头透明度设置为0，达到隐形的效果
                {opacity:0},
                {opacity:1},
            ], {
                delay:this.time_now+1000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
            this.time_now+=1000;
        },
        startAnimate(){
          this.drawArrowStep(this.step_line[0]);
          this.now_no=this.partitionSort_line[0].no;
          for(var i=0;i<this.partitionSort_line.length;i++){
            this.now_no=this.partitionSort_line[i].no
            this.stepArrow.animate([//将箭头透明度设置为0，达到隐形的效果
                {x:this.step_line[i]},
                {x:this.step_line[i+1]},
            ], {
                delay:this.time_now+1000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            });
            this.time_now+=500;


            if(this.partitionSort_line[i].size>0){
                var last;
                if(i==0) last=this.begin_no;
                else last=this.partitionSort_line[i-1].no.toString()
                this.drawArrow1(this.partitionSort_line[i].size,this.partitionSort_line[i].start,this.now_no,last,i);
            }else{ 
                var last;
                if(i==0) last=this.begin_no;
                else last=this.partitionSort_line[i-1].no.toString()
                this.drawArrow0(this.partitionSort_line[i].size,this.partitionSort_line[i].start,this.now_no,last,i);
            }
          }
          var num = (this.all_move_length/this.partitionSort_line.length).toFixed(2)
           this.layer.timeline.setTimeout(() => {
                this.tips_text+='<br/>'+"总共移动了"+this.all_move_length.toString()+"个磁道"+'<br/>'
                this.tips_text+="平均需要移动"+this.all_move_length.toString()+"/"+this.partitionSort_line.length.toString()+"="+num+"个磁道"+'<br/>'
            },this.time_now);
        },
        onSubmit1(){
          this.partition_line=this.partition.partition_input.concat([]);
         
          var sort_line=this.partition.partition_input.concat([]);
          var begin={
            no:this.begin_no
          }
          sort_line.push(begin)
          sort_line.sort((a,b)=>{return a.no-b.no})//重新排序后加载到画布上
            this.partition_line.sort((a,b)=>{return a.no-b.no})
          this.drawAxis(sort_line);
          for(var i=0;i<this.partition_line.length;i++){
              this.partition_line[i].isPass=0;
            }
          console.log(this.partition_line);
          this.SSTF()
          this.now_no=this.begin_no;
          this.FCFS();
          this.tips_text="磁头初始位置为"+this.begin_no.toString()+"号磁道"+'<br/>'
          this.startAnimate();
          console.log('移动路线：')
            console.log(this.moveDistance)

          this.stepArrow.animate([//将箭头透明度设置为0，达到隐形的效果
                {opacity:1},
                {opacity:0},
            ], {
                delay:this.time_now+1000,
                duration: 500,
                iterations: 1,
                fill: 'forwards',
            })
        },
    reset() {
        this.partition.partition_input = [{ np: "" }]
        this.begin_no = "";
        this.layer.removeAllChildren();
        this.tips_text = "",//解说文字
            this.stepArrow = '',//箭头动画元素
            this.step_line = []//记录坐标的实际位置
        this.y = 30;
        this.now_index=-1,
        this.now_no = -1//当前坐标
        this.disk_line = []
        this.axis_pos = 0//下标位置
        this.axis_line = []//保存坐标动画元素
        this.now_partition_pos = ""//记录当前箭头起始位置
        this.all_move_length = 0//总移动距离
        this.moveDistance = []//记录移动距离的数组
        this.time_now = 0//记录延后执行的时间
        this.partitionSort_line = []//最短寻道算法后的排序




        this.total = 0//访问的磁道数
   
        
        },
        handleChange(value) {
            console.log(value);
        },
        //表格变色
        tableRowClassName({row, rowIndex}) {
            if (rowIndex === 1) {
            return 'warning-row';
            } else if (rowIndex === 3) {
            return 'success-row';
            }
            return '';
        },
        random(min, max) {
            return Math.round(Math.random() * (max - min)) + min;
        }

},
mounted() {
  this.init();
},
    
}
</script>