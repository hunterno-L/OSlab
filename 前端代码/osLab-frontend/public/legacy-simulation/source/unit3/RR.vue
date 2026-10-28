<template>
 <div id="outer">
    <el-card class="box-card">
      <div class="setting">

        <el-scrollbar class="scrollbar">
        
        <div class="box-card-setting-title">
        <div class="box-card-setting-title-1">进程调度算法</div>
        <div class="box-card-setting-title-2">时间片轮转（RR）调度算法</div> 
         </div>
          
           <!--时间片太大会退化成fcfs，所以不宜过大，最大是10-->
        <label>时间片大小   </label>
        <el-input-number size="mini" v-model="timepiece" @change="handleChange" :min="1" :max="10"  lable="时间片"></el-input-number>
          
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
    title="时间片轮转调度算法"
    :visible.sync="drawer"
    :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">时间片轮转调度算法</div>
            <div id="drawer-text-content">  
            
             <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">将所有就绪进程按照到达时间的先后次序排成一个队列，进程调度程序总是选择就绪队列中的第一个进程执行，即先来先服务的原则，但仅能运行一个时间片，在使用完一个时间片后，即使进程并未完成其运行，也必须释放（被剥夺）处理机给下一个就绪进程，而被剥夺的进程返回到就绪队列的末尾重新排队，等候再次运行。</div>



             <span style="font-weight:bold; display: block;">特点：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">在时间片轮转调度算法中，时间片的大小对时系统性能的影响很大。若时间片足够大，以至于所有进程都能在一个时间片内执行完毕，则时间同片轮转调度算法就退化为先来先服务调度算法。
若时间片很小，则处理机将在进程间过于频繁地切换使处理机的开销增大，而真正用于运行用户进程的时间将减少。因此，时间片的大小应选择适当时间片的长短通常由以下因素确定：系统的的响应时间、就绪队列中的进程数目和系统的处理能力。</div>
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
           
           <el-table-column
            prop="t_over"
            label="是否为最后一次调度">
            </el-table-column>
        </el-table>

  </div>


 </div>


</template>
<script>

import * as spritejs from 'spritejs';


const { Scene, Sprite, Rect, Label, Group, Path ,Gradient} = spritejs;
const cpupos = 650;
const WAIT1 = 4000;
const ERROR = 3500;

const init_top = 80;
const ANIMATE_MOVE1 = 1000;//动画移动到就绪队列需要1s
export default {
    name: 'RR',
    data() {
        return {
        form: {
       pcbItem0:[
         {
           pcb_name: 'A',
           pcb_arrive: 0,
           pcb_work:10,

           pcb_start: '',
           pcb_end: '',
           pcb_status: 0,
           pcbGroup: "",
           time_wait1: 0,//等待来临
           time_wait2: 0,//等待上CPU
           pcb_status:0

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
           pcb_status:0
           
         },
         {
           pcb_name: 'C',
           pcb_arrive: 1,
           pcb_work:7,

           pcb_start: '',
           pcb_end: '',
           pcb_status: 0,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status:0
         }, 
         {
           pcb_name: 'D',
           pcb_arrive: 2,
           pcb_work:13,

           pcb_start: '',
           pcb_end: '',
           pcb_status:0,
           pcbGroup: "",
           time_wait1: 0,
           time_wait2: 0,
           pcb_status:0
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
      timepiece: 2, //时间片大小默认是2
      pcbSortLine: [],
      drawer:false
            
    }
    },
    methods: {
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
                pos: [0, 132],//!!!50->0
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
                pos: [-15,35 + init_top], //!!!25->-25
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
             zIndex:100
                
            });
            //建组
            const pcbGroup = new Group();
            pcbGroup.attr({
                size: [50, 50],
              pos: [-25, 0],
                
            });
            pcbGroup.append(pcb);
      pcbGroup.append(pcbname);

           
      //this.layer.append(pcbGroup);
      return pcbGroup;

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
            { x: -50, y: 0}, //!!!-25->-50
            { x: 385, y: 0}  //360-25  335->385
        ], { 

            delay:this.pcbSortLine[i].time_wait1,
            duration: 1000,
            iterations: 1,
            fill: 'forwards',
            

        });

        timeline.setTimeout(() => {
            this.wait_line.push(wait_name);
                    this.tips_text+="时刻"+this.pcbSortLine[i].pcb_arrive+"进程【"+wait_name+"】到达就绪队列"+'<br/>'
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

            pcbGroup.animate([
                {x: 385}, //!!!335->385
                {x: 650},  //!!!600->650
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
                {y: 50},
                {y: 200},       
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

    YF(i) {
        if (this.pcbItem[i].pcb_status == 2) {
            return;
        }
        var prestatus = this.pcbItem[i].pcb_status;//标记进程的状态，来计算动画delay时间
        this.pcbItem[i].pcbGroup = this.setPCBGroup(i);
        this.pcbItem[i].pcb_start = Math.max(this.timeNow,this.pcbItem[i].pcb_arrive); //上一个进程结束的时间与该进程到达时间之间较大的值
        this.pcbItem[i].pcb_status = 2;
        this.pcbItem[i].pcb_truework = Math.min(this.pcbItem[i].pcb_work, this.timepiece); //这轮真正处理的时间不超过时间片

        this.pcbItem[i].pcb_end = this.pcbItem[i].pcb_start + this.pcbItem[i].pcb_truework;

        const pcbInfo = {
                name: this.pcbItem[i].pcb_name,

                t_arr: this.pcbItem[i].pcb_arrive,
                t_work: this.pcbItem[i].pcb_truework,
                t_start: this.pcbItem[i].pcb_start,
                t_end: this.pcbItem[i].pcb_end,
                t_week: "",
                t_weightweek: "",
                t_over:"×"
        }
        if (this.pcbItem[i].pcb_work <= this.timepiece) {//能结束,不用再push,能算带权周转时间了
           
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
            
        } else { //不能结束。按照顺序处理i+1
            var item = {
                pcb_name: this.pcbItem[i].pcb_name,
                pcb_work: this.pcbItem[i].pcb_work - this.pcbItem[i].pcb_truework,//还需的CPU时间
                pcb_truework:0,//真实工作了的时间
                pcb_arrive: this.pcbItem[i].pcb_end,
                time_wait1: 0,
                time_wait2:0,
                pcbGroup: "",
                pcb_status: 1,//是运行了一半的
                pcb_start: "",
                pcb_end: ''
            }

            this.pcbItem.push(item);//放到队尾

            
        }

       
        //________________________________________
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
        this.pcbTable.push(pcbInfo);
        this.pcbSortLine.push(this.pcbItem[i]);
        if (this.pcbSortLine.length >= this.pcbItem.length) {
                return;
        }   
        this.YF(i + 1);

        
    },
    async submitForm() { 
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
     
    for(var i=0;i<this.pcbSortLine.length;i++){
        this.startAnimate(i);
    }
      },
     highLight(){

        var replace_text= ["到达就绪队列","运行结束","上CPU"]
        var color_dict = { "运行结束": "rgb(255, 0, 0)", "上CPU": "rgb(148, 201, 125)" ,"剥夺处理机":"rgb(0, 128, 255)","到达就绪队列":"rgb(52, 195, 213)"}
        for(var i=0;i<replace_text.length;i++){
            var color_temp = color_dict[replace_text[i]];
            var replaceString = '<span style="color: ' + color_temp + ';">' + replace_text[i] + "</span>";
            this.tips_text = this.tips_text.replace(RegExp(replace_text[i],'g') ,replaceString);
        }
  
    
    },

        
  },

    
    mounted() {
        this.init();
    },
}
</script>
