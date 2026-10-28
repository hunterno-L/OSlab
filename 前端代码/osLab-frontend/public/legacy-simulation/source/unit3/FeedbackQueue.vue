<template>

<div id="outer">
 <el-card class="box-card">
      <div class="setting">

        <el-scrollbar class="scrollbar">
        
        <div class="box-card-setting-title">
        <div class="box-card-setting-title-1">多级反馈队列调度算法</div>
        <div class="box-card-setting-title-2">多级反馈队列调度算法</div> 
         </div>
          
        <label>队列数  </label>
        <el-input-number size="mini" v-model="queueNum" @change="handleChange1" :min="2" :max="6"  lable="队列数" :rules="{required: true, message: '队列数不能为空', trigger: 'blur'}" prop="queueNum"></el-input-number>
     
        
      
         <!--输入每个队列时间片大小的表格 <el-form-item label="队列个数" prop="queueNum" :rules="{required: true, message: '队列数不能为空', trigger: 'blur'}">
    <el-input-number v-model="queueNum" size="mini" :min="1" :max="10" @change="handleChange1"></el-input-number>
  </el-form-item>-->
         <el-form :model="lineForm" :inline="true" ref="lineForm">



 
        
        <div v-for="(item,index) in lineForm.lineObjs" :key="index">
          <el-form-item label-width="50px">第{{index+1}}级队列的</el-form-item>
          <el-form-item label-width="100px" :rules="{required: true, message: '时间片大小不能为空', trigger: 'blur'}"  label="时间片大小"
               :prop="'lineObjs.' + index + '.size'">
             <el-input-number size="mini" v-model="item.size" :min="1" :max="10"></el-input-number>
           </el-form-item> 
         </div>

         </el-form>
             <el-divider></el-divider>
      
      <!--进程信息的表格-->
       <el-form :model="form"  :inline="true" ref="form">
         <div v-for="(item,index) in form.pcbItem0" :key="index">
           <el-form-item label-width="50px">{{index}}</el-form-item>
           <el-form-item
               label-width="100px"
               label="进程名称"
               :prop="'pcbItem0.' + index + '.pcb_name'"
               :rules="{required: true, message: '名称不能为空', trigger: 'blur'}"
           >

             <el-input size="mini" v-model="item.pcb_name"></el-input>
           </el-form-item>

           <el-form-item
               label-width="100px"
               label="到达时间"
               :prop="'pcbItem0.' + index + '.pcb_arrive'"
               :rules="{required: true, message: '到达时间不能为空', trigger: 'blur'}"
           >
             <el-input-number size="mini" v-model="item.pcb_arrive" @change="handleChange" :min="0"  ></el-input-number>

           </el-form-item>

           <el-form-item
               label-width="100px"
               label="工作时间"
               :prop="'pcbItem0.' + index + '.pcb_work'"
               :rules="{required: true, message: '工作时间不能为空', trigger: 'blur'}"
           >
             <el-input-number size="mini" v-model="item.pcb_work" @change="handleChange" :min="0" ></el-input-number>
           </el-form-item>

           <el-form-item>
             <el-button size="mini" v-if="index+1 == form.pcbItem0.length" @click="addItem" type="primary">增加</el-button>
             <el-button size="mini" v-if="index !== 0" @click="deleteItem(index)" type="danger">删除</el-button>
           </el-form-item>

         </div>


          </el-form>



        </el-scrollbar>
      </div>
      <el-divider></el-divider>
      
      <div id="options">

        

        <el-button size="mini" @click="submitForm()" type="primary">提交</el-button>
        

        <el-button size="mini" type="danger" @click="reset()">重置</el-button>

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

 <el-divider></el-divider>
  <el-drawer
    title="多级反馈队列调度算法"
    :visible.sync="drawer"
    :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">多级反馈队列调度算法</div>
            <div id="drawer-text-content">  
            
             <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">设置多个就绪队列，并为各个队列赋予不同优先级。第一级队列优先级最高，其余队列优先级逐次降低。赋予各个队列中进程执行时间片的大小各不相同，优先级越高的队列中，每个进程时间片越小。新进程进入内存后首先放入第1级队列末尾，按FCFS原则排队等待调度，当进程在当前队列的一个时间片内未执行完时，将其加到下一级队列的队尾等待调度。仅当高优先级的队列为空时，调度程序才调度比其优先级低的队列中的进程。</div>



             <span style="font-weight:bold; display: block;">特点：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">多级反馈队列调度算法是时间片轮转调度算法和优先级调度算法的综合与发展，通过动态调整进程优先级和时间片大小，多级反馈队列调度算法可以兼顾多方面的系统目标。</div>
            </div>
        </div>
    </el-drawer>

   <div id="canvas" style="width: 1200px; height: 500px; position:relative">

  
   <div id="adaptive" style="width: 860px; height: 500px; background: #fff; position:absolute;
   margin-left: 80px;"></div>
    <transition name="fade">
    <div id="tips1" v-if="show" v-html="tips_text">
    </div>
    </transition>
    
    </div>

   <el-divider></el-divider>
  <div id="info">
    <el-table
            :data="pcbTable"
            style="width: 100%"
            :row-class-name="tableRowClassName">
            <el-table-column
            prop="name"
            label="进程名"
            >
            </el-table-column>
            <el-table-column
            prop="t_queue"
            label="队列序号"
           >
            </el-table-column>
            <el-table-column
            prop="t_arr"
            label="到达时间"
            >
            </el-table-column>
            <el-table-column
            prop="t_work"
            label="工作时间"

            >
            </el-table-column>
            <el-table-column
            prop="t_start"
            label="开始时间" 
            >
            </el-table-column>
            <el-table-column
            prop="t_end"
            label="完成时间"

            >
            </el-table-column>
            <el-table-column
            prop="t_week"
            label="周转时间"

            >
            </el-table-column>
            <el-table-column
            prop="t_weightweek"
            label="带权周转时间"
            >
            </el-table-column>
           
           <el-table-column
            prop="t_over"
            label="是否为最后一次调度"

            >
            </el-table-column>
        </el-table>

  </div>



</div>
     
   
   
   


            
       
    
</template>

<script>

import * as spritejs from 'spritejs';


const { Scene, Sprite, Rect, Label, Group, Path ,Gradient} = spritejs;
const init_top = 30;
const cpupos = 700;
const ANIMATE_MOVE1 = 1000;
export default {
    name: 'FeedbackQueue',
    data() {
      return {
        queueNum: 3,
        lineForm: {
          lineObjs: [
            {
              size: 1,//每个队列的时间片大小，

            },
            {
              size: 2,

            },
            {
              size: 4,

            },
            

          ]
        },
        form: {
          pcbItem0: [
          {
           pcb_name: 'A',
           pcb_arrive:1,
           pcb_work:13,

           pcb_start: '',
           pcb_end: '',
           pcb_status: 0,
           pcbGroup: "",
           time_wait1: 0,//等待来临
           time_wait2: 0,//等待上CPU
           pcb_status: 0,
           pcb_truework:0,
           queueIndex:0 //在第一级队列

         },
         {
           pcb_name: 'B',
           pcb_arrive: 0,
           pcb_work:8,

           pcb_start: '',
           pcb_end: '',
           pcb_status: 0,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status: 0,
           pcb_truework:0,
           queueIndex:0 //在第一级队列
           
         },
         {
           pcb_name: 'C',
           pcb_arrive: 1,
           pcb_work:6,

           pcb_start: '',
           pcb_end: '',
           pcb_status: 0,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status: 0,
           pcb_truework:0,
           queueIndex:0 //在第一级队列
            }, 
          {
           pcb_name: 'D',
           pcb_arrive: 1,
           pcb_work:5,

           pcb_start: '',
           pcb_end: '',
           pcb_status: 0,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status: 0,
           pcb_truework:0,
           queueIndex:0 //在第一级队列
            }, 

         

         ]
          
        },
        pcbItem:[],
        timeNow: 0,
        playbackRate:1.0,
        pcbTable: [],
        tips_text: '',
        linetext: '',
        show: false,
        wait_line: [],//就绪队列的进程名，用于转换为文字
        layer: "",


        pcbSortLine: [],
        drawer:false

           
        }
    },
  methods: {
    handleChange(value) {
      console.log(value);
    },
    handleChange1(value) {
      console.log(value);
      this.lineForm.lineObjs = [];
      for (var i = 0; i < this.queueNum; i++){
        var item = {
          size: 1,

        }
        this.lineForm.lineObjs.push(item);
      }
      this.changeCanvas();
    },

    deleteItem(index) {
            this.form.pcbItem0.splice(index, 1);
            console.log(this.form.pcbItem0, "删除");
    },
    addItem() {
           /**新建一个pcb,默认值全空*/
            this.form.pcbItem0.push({
              pcb_name: '',
              pcb_arrive: '',
              pcb_work: '',
              pcb_start: '',
              pcb_end: '',
              pcb_status: false,
              pcb_truework:0,
              pcbGroup: "",
              time_wait1: 0,
              pcb_status: 0,
              queueIndex:0
            });

      console.log(this.form.pcbItem0);


        },
    changeCanvas() {
      this.layer.removeAllChildren();
        for (var i = 0; i < this.lineForm.lineObjs.length; i++){
         //就绪队列
      const line  = new Sprite({
          normalize: true,
          pos: [50, 80*i + init_top],
          anchor: [0, 0],
          size: [360, 50],
          bgcolor: 'white',
          borderWidth: 2,
      });
      this.layer.append(line);  
      }
      //cpu
       const rect = new Rect({
        normalize: true,
        pos: [cpupos, init_top+30],
        anchor: [0, 0],
        size: [100, 100],
        lineWidth: 2,
        strokeColor: 'black',
      });
      this.layer.append(rect);

     //cpu文字说明
      const cputext = new Label('CPU');
      cputext.attr({
       pos: [cpupos-25, 50+init_top],
        anchor: [0, 0],
        fillColor: this.GLOBAL.COLOR_FONT,
        font: '25px "黑体"',
        lineHeight: 100,
        textAlign: 'center',
      });
      this.layer.append(cputext);
    },
    init() {
      const container = document.getElementById("adaptive");
      const scene = new Scene({ // 初始化场景scene，它不是canvas，只是一个用来包裹canvas的div元素
        container,
        width: 1000,
        height: 500,
        mode: 'stickyWidth',
      });
      this.layer = scene.layer();// 创建背景图层layer，这个就是真正意义上的canvas了


      for (var i = 0; i < this.lineForm.lineObjs.length; i++){
      //就绪队列
      const line  = new Sprite({
          normalize: true,
          pos: [50, 80*i + init_top],
          anchor: [0, 0],
          size: [360, 50],
          bgcolor: 'white',
          borderWidth: 2,
      });
      this.layer.append(line);  
      }
      //cpu
       const rect = new Rect({
        normalize: true,
        pos: [cpupos, init_top+30],
        anchor: [0, 0],
        size: [100, 100],
        lineWidth: 2,
        strokeColor: 'black',
      });
      this.layer.append(rect);

     //cpu文字说明
      const cputext = new Label('CPU');
      cputext.attr({
       pos: [cpupos-25, 50+init_top],
        anchor: [0, 0],
        fillColor: this.GLOBAL.COLOR_FONT,
        font: '25px "黑体"',
        lineHeight: 100,
        textAlign: 'center',
      });
      this.layer.append(cputext);



      
    },
    setPCBGroup(i)
    {

       const pcb = new Rect({
                name:"pcb",
                normalize: true,
                pos: [50, 57+80*this.pcbItem[i].queueIndex],//!!!50->0
                size: [50, 50],
                fillColor: this.GLOBAL.COLOR_PCB_READY,
                zIndex: 10,
                lineWidth: 2,
                strokeColor:this.GLOBAL.COLOR_FONT
       });
          
       //建进程名

            const pcbname = new Label(this.pcbItem[i].pcb_name);
            pcbname.attr({
                name:"pcbname",
                pos: [35,80*this.pcbItem[i].queueIndex + init_top+10], //!!!25->-25
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
             zIndex:100
                
            });
            //建组
            const pcbGroup = new Group();
            pcbGroup.attr({
                size: [50, 50],
              pos: [-75, 0],
                
            });
            pcbGroup.append(pcb);
      pcbGroup.append(pcbname);

           
      //this.layer.append(pcbGroup);
      return pcbGroup;

    },
    removeWaitName(str) {
    for(var i=0;i<this.wait_line.length;i++){
              if(this.wait_line[i] == str){
                  
                  this.wait_line.splice(i, 1); 
                  console.log('找' + str + "剩" + this.wait_line)
                  
                  break;
              }
          }
    },
        //表格变色
    tableRowClassName({row, rowIndex}) {
      if (row.t_week != "") {
      return 'success-row';
      }
     return '';
        },
    async submitForm() {
      console.log(this.lineForm.lineObjs);
       this.pcbItem = this.form.pcbItem0.concat([]);//深拷贝，返回一个新数组
          //按照先来后到排个序，才合理。
      for (var i = 0; i < this.pcbItem.length-1; i++)
      {
        for (var j = i + 1; j < this.pcbItem.length; j++)
        {
          if (this.pcbItem[i].pcb_arrive > this.pcbItem[j].pcb_arrive)
          {
            var tmp = this.pcbItem[i];
            this.pcbItem[i] = this.pcbItem[j];
            this.pcbItem[j] = tmp;
          }
        }
      }
   
      this.timeNow = this.pcbItem[0].pcb_arrive;
      await this.YF(0);

      //改变waittime

      console.log("下面的是pcbSorline");
      console.log(this.pcbSortLine);

    
     

      for(var i=0;i<this.pcbSortLine.length;i++){
          this.startAnimate(i);
      }
    },
    async startAnimate(i)
    {
        const timeline = this.layer.timeline;
        const pcbGroup = this.pcbSortLine[i].pcbGroup;
    
        const pcb = pcbGroup.childNodes[0];//rect对象
        this.layer.append(pcbGroup);//group对象


        var wait_name = pcbGroup.childNodes[1].text;
            
        console.log('delay' + this.pcbSortLine[i].time_wait1 + this.pcbSortLine[i].time_wait2);

        //-------------进入就绪队列
        pcbGroup.animate([
            { x: -50}, //!!!-25->-50
            { x: 338}  //360-25  335->385
        ], { 

            delay:this.pcbSortLine[i].time_wait1,
            duration: 1000,
            iterations: 1,
            fill: 'forwards',
            

        });

        timeline.setTimeout(() => {
            this.wait_line.push(wait_name);
                    this.tips_text+="时刻"+this.pcbSortLine[i].pcb_arrive+"进程【"+wait_name+"】到达第"+(this.pcbSortLine[i].queueIndex+1)+"级就绪队列"+'<br/>'
          this.linetext = "就绪队列：" + this.wait_line.toString();
          this.highLight();
        }, this.pcbSortLine[i].time_wait1);

        console.log(i+'到就绪队列的时间：' + this.pcbSortLine[i].time_wait1);

        //-----------------------上CPU，在为了复现过程，都要在就绪队列停2s
            pcb.animate([
                    {fillColor: this.GLOBAL.COLOR_PCB_READY},
                    {fillColor: this.GLOBAL.COLOR_PCB_RUN},
                    
                ], {
                    delay:this.pcbSortLine[i].time_wait2+2000,//2->1
                    duration: 1000,
                    iterations: 1,
                    fill: 'forwards',
            });
//80*this.pcbItem[i].queueIndex + init_top
            pcbGroup.animate([
                {x: 338,}, //!!!335->385
                {x: 650,y:-this.pcbSortLine[i].queueIndex*80},  //!!!600->650
            ], {
                delay:this.pcbSortLine[i].time_wait2+2000,//2->1
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
            });
        timeline.setTimeout(() => {
            this.removeWaitName(wait_name);
                this.tips_text+="时刻"+this.pcbSortLine[i].pcb_start+":进程【"+wait_name+"】上CPU"+'<br/>'
                this.linetext = "就绪队列：" + this.wait_line.toString();
                this.highLight();
            },this.pcbSortLine[i].time_wait2+2000);//2000->1
        console.log('到CPU的延迟时间：' + this.pcbSortLine[i].time_wait2);
        //运行结束

        //颜色变化
        pcb.animate([
                    {fillColor: this.GLOBAL.COLOR_PCB_RUN},
                    {fillColor: this.GLOBAL.COLOR_PCB_END},
                ], {
                    delay:this.pcbSortLine[i].time_wait2+2000+this.pcbSortLine[i].pcb_truework * 1000,
                    duration: 1000,
                    iterations: 1,
                    fill: 'forwards',
        });

        timeline.setTimeout(() => {
          

        
        if(this.pcbSortLine[i].pcb_truework==this.pcbSortLine[i].pcb_work) //运行结束放弃CPU       
                this.tips_text += "时刻" + this.pcbSortLine[i].pcb_end + ":进程【" + wait_name + "】运行结束" + '<br/>'
        else //运行一半被剥夺CPU
            this.tips_text += "时刻" + this.pcbSortLine[i].pcb_end + ":进程【" + wait_name + "】用尽时间片后被剥夺处理机" + '<br/>'
        this.highLight();
        }, this.pcbSortLine[i].time_wait2 + 2000 + this.pcbSortLine[i].pcb_truework * 1000);


        pcbGroup.animate([
                {y:-this.pcbSortLine[i].queueIndex*80},
                {y: 200-this.pcbSortLine[i].queueIndex*80},       
        ], {
                
                delay:this.pcbSortLine[i].time_wait2 + 2000 + this.pcbSortLine[i].pcb_truework * 1000,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
        });
        console.log('下CPU的延迟时间：' + this.pcbSortLine[i].time_wait2 + 2000 + this.pcbSortLine[i].pcb_truework * 1000);
        

        timeline.setTimeout(() => {
                pcbGroup.remove();
            },this.pcbSortLine[i].time_wait2 + 2000 + this.pcbSortLine[i].pcb_truework * 1000+2000); //3s后消失掉
    

    },
    highLight(){

        var replace_text= ["就绪队列","运行结束","上CPU","剥夺处理机"]
        var color_dict = { "运行结束": "rgb(255, 0, 0)", "上CPU": "rgb(148, 201, 125)" ,"剥夺处理机":"rgb(255, 0, 0)","就绪队列":"rgb(52, 195, 213)"}
        for(var i=0;i<replace_text.length;i++){
            var color_temp = color_dict[replace_text[i]];
            var replaceString = '<span style="color: ' + color_temp + ';">' + replace_text[i] + "</span>";
            this.tips_text = this.tips_text.replace(RegExp(replace_text[i],'g') ,replaceString);
        }
  
    
    },
    YF(i) {
      if (this.pcbItem[i].pcb_status == 2) {
        return;
      }
      var prestatus = this.pcbItem[i].pcb_status;
      var nextPos;
      var nextPCB;//如果抢占则可以确定
      console.log('yf=' + i);
      this.pcbItem[i].pcbGroup = this.setPCBGroup(i);
      this.pcbItem[i].pcb_start = Math.max(this.timeNow,this.pcbItem[i].pcb_arrive); //上一个进程结束的时间与该进程到达时间之间较大的值
      this.pcbItem[i].pcb_status = 2;
      this.pcbItem[i].pcb_truework = Math.min(this.lineForm.lineObjs[this.pcbItem[i].queueIndex].size,this.pcbItem[i].pcb_work) ;


    //判断是否有新进程到达第一级队列，如果有的话要被剥夺处理机---
      var over = true;
      for (var j = 0; j < this.pcbItem.length; j++){
        if (this.pcbItem[j].pcb_status!=2&&this.pcbItem[j].queueIndex<this.pcbItem[i].queueIndex&&this.pcbItem[j].pcb_arrive <this.pcbItem[i].pcb_truework + this.pcbItem[i].pcb_start) {
          over = false;
          //nextPos = j;
          nextPCB = this.pcbItem[j];
          this.pcbItem[i].pcb_truework = this.pcbItem[j].pcb_arrive - this.pcbItem[i].pcb_start;
          break;
        }
      }


      this.pcbItem[i].pcb_end = this.pcbItem[i].pcb_start + this.pcbItem[i].pcb_truework;


      const pcbInfo = {
                name: this.pcbItem[i].pcb_name,
                t_arr: this.pcbItem[i].pcb_arrive,
                t_work: this.pcbItem[i].pcb_truework,
                t_start: this.pcbItem[i].pcb_start,
                t_end: this.pcbItem[i].pcb_end,
                t_week: "",
                t_weightweek: "",
                t_over: "×",
                t_queue: this.pcbItem[i].queueIndex+1
                
      }
      if (over == true)
      {

      //所需的工作时间大于当前队列的时间片（且不是最后一个队列），就要进入下一队列
      if (this.pcbItem[i].pcb_work > this.lineForm.lineObjs[this.pcbItem[i].queueIndex].size) {
        var item = {
            pcb_name: this.pcbItem[i].pcb_name,
            pcb_work: this.pcbItem[i].pcb_work - this.pcbItem[i].pcb_truework,//还需的CPU时间
            pcb_truework:0,//真实工作了的时间
            pcb_arrive: this.pcbItem[i].pcb_end,
          queueIndex: this.pcbItem[i].queueIndex,//下面会判断是否需要进入下一个队列
            time_wait1: 0,
            time_wait2:0,
            pcbGroup: "",
            pcb_status: 1,//是运行了一半的
            pcb_start: "",
            pcb_end: ''
        }
        if (this.pcbItem[i].queueIndex != this.queueNum - 1) {
          item.queueIndex++;//进入下一个队列
        }

        this.pcbItem.push(item);//按照先来先服务的理念是没问题的
      } else { //能在此时间片内运行结束！可以算出来周转时间啥的了
          var initWork = 0;
            var initArrive = 0;
            for (var k = 0; k < this.form.pcbItem0.length; k++){
                if (this.form.pcbItem0[k].pcb_name == this.pcbItem[i].pcb_name) {
                    initWork = this.form.pcbItem0[k].pcb_work;
                    initArrive = this.form.pcbItem0[k].pcb_arrive;
                    break;
                }
            }

            pcbInfo.t_week = this.pcbItem[i].pcb_end - initArrive;
            var number=pcbInfo.t_week/initWork ;
            pcbInfo.t_weightweek = number.toFixed(2);
            pcbInfo.t_over='√'
      }
      }
      else {
        //还在这个队列
        var item = {
          pcb_name: this.pcbItem[i].pcb_name,
          pcb_work: this.pcbItem[i].pcb_work - this.pcbItem[i].pcb_truework,//还需的CPU时间
          pcb_truework: 0,//真实工作了的时间
          pcb_arrive: this.pcbItem[i].pcb_end,
          queueIndex: (this.pcbItem[i].queueIndex+1),//由于被抢占了，所以继续在这个队列
          time_wait1: 0,
          time_wait2: 0,
          pcbGroup: "",
          pcb_status: 1,//是运行了一半的
          pcb_start: "",
          pcb_end: ''
        };
        this.pcbItem.push(item);//按照先来先服务的理念是没问题的

        
      }
//--------------------计算动画等待时间
      if (i == 0) {//第一个进程直接上，不停
        this.pcbItem[i].time_wait1 = 0;
        this.pcbItem[i].time_wait2 = 0;
      }
      else
        {
            const preProcess = this.pcbSortLine[this.pcbSortLine.length - 1];
            this.pcbItem[i].time_wait1 = (this.pcbItem[i].pcb_arrive - this.pcbItem[0].pcb_arrive) * 1000;
            if (prestatus == 1)
            {
                this.pcbItem[i].time_wait1 += 3000;//truework

            }
            
            if (preProcess.pcb_end < this.pcbItem[i].pcb_arrive) {
                //来得晚，到后1s
                this.pcbItem[i].time_wait2 = this.pcbItem[i].time_wait1;
            } else {
                this.pcbItem[i].time_wait2 =( preProcess.pcb_end-this.pcbItem[0].pcb_arrive)*1000+ANIMATE_MOVE1;
             }

            


            
        }
        


      this.timeNow= this.pcbItem[i].pcb_end;
      this.pcbSortLine.push(this.pcbItem[i]);

      console.log( this.pcbSortLine);
      this.pcbTable.push(pcbInfo);
//-------------------找到下一个进程在PCBItem中的位置
      for (var p = 0; p < this.pcbItem.length - 1; p++){
        for (var q = p + 1; q < this.pcbItem.length; q++){
          if ((this.pcbItem[p].queueIndex > this.pcbItem[q].queueIndex)||((this.pcbItem[p].queueIndex == this.pcbItem[q].queueIndex)&&(this.pcbItem[p].pcb_arrive > this.pcbItem[q].pcb_arrive)) ) {
             var tmp = this.pcbItem[p];
            this.pcbItem[p] = this.pcbItem[q];
            this.pcbItem[q] = tmp;
          }
        }
      }
      var arrived = false;//在此之前有没有进程到达
      for (var r = 0; r < this.pcbItem.length; r++) { 
        if (this.pcbItem[r].pcb_arrive <= this.timeNow) {
          arrived = true;
          break;
          }
      }
      if (this.pcbSortLine.length >=this.pcbItem.length) {
              return;
      }
      if (over) {
       for (var t = 0; t < this.pcbItem.length; t++){
        if (this.pcbItem[t].pcb_status != 2&&((arrived&&this.pcbItem[t].pcb_arrive<=this.timeNow)||!arrived)) {
          nextPos = t;
          break;
        }
      }
      }
      else
      {
        for (var g = 0; g < this.pcbItem.length; g++) { 
        if (this.pcbItem[g]==nextPCB) {
          nextPos = g;
          break;
          }
      }
      }
    
     
        this.YF(nextPos);

      

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
  reset()
  {
      this.form.pcbItem0 =
        [{
          pcb_name: '',
            pcb_arrive: 0,
            pcb_work: 1,
            pcb_start: '',
            pcb_end: '',
            pcb_status: false,
            pcbGroup: "",
          time_wait1: 0,
          time_wait2: 0,
          pcb_status: 0,
          queueIndex:0
        }
        ];
      this.pcbItem = [];
      this.queueNum = 3;//默认是三
      this.lineForm.lineObjs =
      [
        {
          size: 1,//每个队列的时间片大小，
        },
        {
          size: 2,//每个队列的时间片大小，

        },
        {
          size: 3,//每个队列的时间片大小，

        }

      ];
      console.log(this.lineForm.lineObjs);
      this.layer.removeAllChildren();
    this.pcbTable = [];
    
      this.tips_text = '';
      this.pcbSortLine = [];
      this.timepiece = 2; //时间片恢复默认值
    this.playbackRate = 1;
      this.init();
    },

    sure(form) {
            this.$refs[form].validate(valid => {
                if (valid) {
                alert("submit!");
                } else {
                console.log("error submit!!");
                return false;
                }
            });
    },



    },
    mounted(){
      this.init();
    }
}
</script>