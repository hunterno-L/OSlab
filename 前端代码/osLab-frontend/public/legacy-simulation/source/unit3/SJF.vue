<template>
 <div id="outer">
    <el-card class="box-card">
      <div class="setting">

        <el-scrollbar class="scrollbar">
        
        <div class="box-card-setting-title">
        <div class="box-card-setting-title-1">进程调度算法</div>
        <div class="box-card-setting-title-2">短作业优先（SJF）调度算法</div> 
         </div>
          
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
         
        <el-button size="mini" type="primary" @click="submitForm()">提交</el-button>
        


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
    title="短作业优先算法"
    :visible.sync="drawer"
    :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">短作业优先算法</div>
            <div id="drawer-text-content">  
            
             <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">短作业优先调度算法是从就绪队列中选择一个或若干个估计运行时间最短的作业，将它们调入内存运行。</div>



             <span style="font-weight:bold; display: block;">特点：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">该算法的平均等待时间、平均周转时间最少；该算法对长作业不利，长作业的周转时间增加，由于总是优先调度短作业，将导致长作业发生“饥饿现象”。</div>
            </div>
        </div>
    </el-drawer>

   <div id="canvas" style="width: 1200px; height: 500px; position:relative">

  
   <div id="adaptive" style="width: 860px; height: 500px; background: #fff; position:absolute;margin-left: 80px;"></div>
  
    <transition name="fade">
    <div id="tips1" v-if="show" v-html="tips_text">
    </div>
    </transition>
    <transition name="fade">
        <div id="tips2" v-if="show" v-html="linetext">
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
            width="180">
            </el-table-column>
            <el-table-column
            prop="t_arr"
            label="到达时间"
            width="180">
            </el-table-column>
            <el-table-column
            prop="t_work"
            label="工作时间">
            </el-table-column>
            <el-table-column
            prop="t_start"
            label="开始时间">
            </el-table-column>
            <el-table-column
            prop="t_end"
            label="完成时间">
            </el-table-column>
            <el-table-column
            prop="t_week"
            label="周转时间">
            </el-table-column>
            <el-table-column
            prop="t_weightweek"
            label="带权周转时间">
            </el-table-column>
        </el-table>

  </div>

  
 </div>
</template>

<script>

import * as spritejs from 'spritejs';

const { Scene, Sprite, Rect, Label, Group, Path ,Gradient} = spritejs;
const cpupos = 650;
const init_top = 80;

export default {
  name: "SJF",
    data() {
    return {
     form: {
       pcbItem0:[
         {
           pcb_name: 'A',
           pcb_arrive: 1,
           pcb_work:8,

           pcb_start: '',
           pcb_end: '',
           pcb_status: false,
           pcbGroup: "",
           time_wait1: 0,//等待来临
           time_wait2: 0,//等待上CPU
           pcb_status:0

         },
         {
           pcb_name: 'B',
           pcb_arrive: 4,
           pcb_work:2,

           pcb_start: '',
           pcb_end: '',
           pcb_status: false,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status:0
           
         },
         {
           pcb_name: 'C',
           pcb_arrive: 2,
           pcb_work:10,

           pcb_start: '',
           pcb_end: '',
           pcb_status: false,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status:0
         }, 
         {
           pcb_name: 'D',
           pcb_arrive: 3,
           pcb_work:3,

           pcb_start: '',
           pcb_end: '',
           pcb_status: false,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status:0
         }
       ]

      },
      pcbItem:[],
      timeNow: 0,
      pause: false,

      pcbTable: [],
      tips_text: '',
      linetext: '',
      show: false,
      wait_line: [],//就绪队列的进程名，用于转换为文字
      layer: "",
      sortPosArr: [0],//存储上CPU的顺序下标，第一个上CPU的肯定是最先来的(排序后下标为0)
      playbackRate: 1.0,
      drawer:false
      

    }
  },
  methods: {
    reset()
    {
      this.form.pcbItem0 = [{ pcb_name: '',
              pcb_arrive: 0,
              pcb_work: 1,
              pcb_start: '',
              pcb_end: '',
              pcb_status: false,
              pcbGroup: "",
              time_wait1: 0,
              pcb_status:0}];
      this.pcbItem = [];

      console.log(this.layer);
      this.layer.removeAllChildren();
      this.pcbTable = [];
      this.tips_text = '';
      this.sortPosArr=[0]
      this.init();

      
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
    init()
    {
    const container = document.getElementById("adaptive");
      const scene = new Scene({ // 初始化场景scene，它不是canvas，只是一个用来包裹canvas的div元素
        container,
        width: 1000,
        height: 500,
        mode: 'stickyWidth',
      });
      this.layer = scene.layer();// 创建背景图层layer，这个就是真正意义上的canvas了
      
         const rect = new Rect({
        normalize: true,
        pos: [cpupos, init_top+50],
        anchor: [0, 0],
        size: [100, 100],
        lineWidth: 2,
        strokeColor: 'black',
      });
      this.layer.append(rect);

     //cpu文字说明
      const cputext = new Label('CPU');
      cputext.attr({
       pos: [cpupos-25, 60+init_top],
        anchor: [0, 0],
        fillColor: this.GLOBAL.COLOR_FONT,
        font: '25px "黑体"',
        lineHeight: 100,
        textAlign: 'center',
      });
      this.layer.append(cputext);


      //就绪队列
      const line  = new Sprite({
          normalize: true,
          pos: [50, 25 + init_top],
          anchor: [0, 0],
          size: [360, 50],
          bgcolor: 'white',
          borderWidth: 2,
      });
      this.layer.append(line);  

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
              pcbGroup: "",
              time_wait1: 0,
              pcb_status:0
            });

      console.log(this.form.pcbItem0);


    },

    setPCBGroup(i)
    {

       const pcb = new Rect({
                name:"pcb",
                normalize: true,
                pos: [50, 132],
                size: [50, 50],
                fillColor: this.GLOBAL.COLOR_PCB_READY,
                zIndex: 10,
                lineWidth: 2,
                strokeColor:this.GLOBAL.COLOR_FONT
       });
          
       //建进程名

      //console.log(this.pcbItem[i]);
            const pcbname = new Label(this.pcbItem[i].pcb_name);
            pcbname.attr({
                name:"pcbname",
                pos: [35,35 + init_top],
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
        async startAnimate(i)
    {

      
      const timeline = this.layer.timeline;
      const pcbGroup = this.pcbItem[i].pcbGroup;
  
      const pcb = pcbGroup.childNodes[0];//rect对象
      this.layer.append(pcbGroup);//group对象


      var wait_name = pcbGroup.childNodes[1].text;
        
      console.log('delay' + this.pcbItem[i].time_wait1 + this.pcbItem[i].time_wait2);

      //-------------进入就绪队列
      pcbGroup.animate([
        { x: -25, y: 0},
        { x: 335, y: 0}  //360-25
      ], { 

        delay:this.pcbItem[i].time_wait1,
        duration: 1000,
        iterations: 1,
        fill: 'forwards',
        

      });

       timeline.setTimeout(() => {
         this.wait_line.push(wait_name);
                this.tips_text+="时刻"+this.pcbItem[i].pcb_arrive+":进程【"+wait_name+"】到达就绪队列"+'<br/>'
         this.linetext = "就绪队列：" + this.wait_line.toString();

         this.highLight();
       }, this.pcbItem[i].time_wait1);

      //-----------------------上CPU，在为了复现过程，都要在就绪队列停2s
         pcb.animate([
                {fillColor: this.GLOBAL.COLOR_PCB_READY},
                {fillColor: this.GLOBAL.COLOR_PCB_RUN},
                
            ], {
                delay:this.pcbItem[i].time_wait2+2000,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
         });

        pcbGroup.animate([
              {x: 335},
              {x: 600},  
          ], {
              delay:this.pcbItem[i].time_wait2+2000,
              duration: 1000,
              iterations: 1,
              fill: 'forwards',
          });


       timeline.setTimeout(() => {
         this.removeWaitName(wait_name);
              this.tips_text+="时刻"+this.pcbItem[i].pcb_start+":进程【"+wait_name+"】上CPU"+'<br/>'
         this.linetext = "就绪队列：" + this.wait_line.toString();
              this.highLight();
          },this.pcbItem[i].time_wait2+2000);
      



    //运行结束

    //颜色变化
      pcb.animate([
                {fillColor: this.GLOBAL.COLOR_PCB_RUN},
                {fillColor: this.GLOBAL.COLOR_PCB_END},
            ], {
                delay:this.pcbItem[i].time_wait2+2000+this.pcbItem[i].pcb_work * 1000,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
      });

     timeline.setTimeout(() => {
               
       this.tips_text += "时刻" + this.pcbItem[i].pcb_end + ":进程【" + wait_name + "】运行结束" + '<br/>';
       this.highLight();
     }, this.pcbItem[i].time_wait2 + 2000 + this.pcbItem[i].pcb_work * 1000);


    pcbGroup.animate([
            {y: 50},
            {y: 200},       
        ], {
            delay:this.pcbItem[i].time_wait2 + 2000 + this.pcbItem[i].pcb_work * 1000,
            duration: 1000,
            iterations: 1,
            fill: 'forwards',
        });
      

       timeline.setTimeout(() => {
            pcbGroup.remove();
        },this.pcbItem[i].time_wait2 + 2000 + this.pcbItem[i].pcb_work * 1000+3000); //3s后消失掉
  



     
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
  handleChange(value) {
      /* console.log(value); */
    },
      //表格变色
    tableRowClassName({row, rowIndex}) {
      if (row.t_week != "") {
      return 'success-row';
      }
     return '';
    },
    async submitForm() {


      console.log(this.form.pcbItem0);
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

      console.log(this.pcbItem);
      await this.YF(0);   
      console.log(this.sortPosArr);  
      for(var i=0;i<this.pcbItem.length;i++){
          this.startAnimate(i);
      }
    },
    YF(i) {
      if (i >= this.pcbItem.length)
      {
        return;
      }
      //生成进程的group对象
      this.pcbItem[i].pcbGroup = this.setPCBGroup(i);

      //设置当前时刻
      if (i >= 1) {
        this.timeNow = Math.max((this.timeNow + this.pcbItem[this.sortPosArr[this.sortPosArr.length - 2]].pcb_work), this.pcbItem[i].pcb_arrive);
        console.log(this.pcbItem[this.sortPosArr[this.sortPosArr.length - 2]]);
      }
      else
      {
        this.timeNow = this.pcbItem[i].pcb_arrive;
        console.log('开始时刻：'+this.timeNow);
      }

      //计算进程的开始时间和结束时间
      this.pcbItem[i].pcb_start = this.timeNow;
      this.pcbItem[i].pcb_end = this.pcbItem[i].pcb_start + this.pcbItem[i].pcb_work;


      //计算等待时间1，为了建立进程的动画延时
     this.pcbItem[i].time_wait1 = (this.pcbItem[i].pcb_arrive - this.pcbItem[0].pcb_arrive) * 1000;


      //计算等待时间2，为了上CPU的动画延时。
      if(i>=1)
      {
       

        const preProcess = this.pcbItem[this.sortPosArr[this.sortPosArr.length - 2]];
        if (preProcess.pcb_end <= this.pcbItem[i].pcb_arrive)
        {
          //等2s直接上
            this.pcbItem[i].time_wait2=this.pcbItem[i].time_wait1+3000
        }
        else
        {
            //等前面的运行结束，并迟1s再上,避免覆盖前一个的动画。
            this.pcbItem[i].time_wait2=(preProcess.pcb_end-this.pcbItem[0].pcb_arrive)*1000+1000;   
        }
      }
      else
      {
        this.pcbItem[i].time_wait2 = this.pcbItem[i].time_wait1 + 1000;
      }
      const pcbInfo = {
        name: this.pcbItem[i].pcb_name,
        t_arr: this.pcbItem[i].pcb_arrive,
        t_work: this.pcbItem[i].pcb_work,
        t_start: this.pcbItem[i].pcb_start,
        t_end: this.pcbItem[i].pcb_end,
        t_week: "",
        t_weightweek:""
      }
      
      pcbInfo.t_week = this.pcbItem[i].pcb_end - this.pcbItem[i].pcb_arrive;
      pcbInfo.t_weightweek = (pcbInfo.t_week / pcbInfo.t_work).toFixed(2);

      this.pcbTable.push(pcbInfo);
        
      console.log('进程' + this.pcbItem[i].pcb_name + '开始的时间为' + this.timeNow);
      this.pcbItem[i].pcb_status = 1;//已经被挑选进入排序数组了


        //全都加入了
       if(this.sortPosArr.length==this.pcbItem.length)
       {
         return;
       }

      //短作业优先就要挑选push到sortPOSArr的是谁
      var pos = -1;

      //找到第一个最早来的
      for (var k = 0; k < this.pcbItem.length; k++)
      {
        if (this.pcbItem[k].pcb_status == 0)
        {
          pos = k;
          break;
        }
      }



      
      var flag = -1;
      
     for (var j=1; j < this.pcbItem.length&&this.pcbItem[j].pcb_arrive <= this.pcbItem[i].pcb_end; j++)
      {

        flag = 1;
        if (this.pcbItem[pos].pcb_work > this.pcbItem[j].pcb_work&&this.pcbItem[j].pcb_status == 0)
        {
          pos = j;
        }

      }
      

      //如果都没到达，就选其后即可
      if (flag == -1)
      {
        console.log('flag=-1');
        pos = i + 1;
      }
      else
      {
        console.log('flag!=-1');
      }
      this.sortPosArr.push(pos);//便于触发动画

    
      console.log(pos);
      this.YF(pos);
      
    }
  },
  
  mounted() {

    this.init();

    },
}
</script>

<style scoped>

</style>