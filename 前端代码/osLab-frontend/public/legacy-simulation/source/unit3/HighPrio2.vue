<template>

 <div id="outer">
    <el-card class="box-card">
      <div class="setting">

        <el-scrollbar class="scrollbar">
        <div class="box-card-setting-title">
        <div class="box-card-setting-title-1">进程调度算法</div>
        <div class="box-card-setting-title-2">优先级（剥夺式）调度算法</div> 
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
               label="优先数"
               :prop="'pcbItem0.' + index + '.prior'"
               :rules="{required: true, message: '优先数不能为空', trigger: 'blur'}"
           >
             <el-input-number size="mini" v-model="item.prior" @change="handleChange" :min="1" ></el-input-number>
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
             <el-input-number size="mini" v-model="item.pcb_work" @change="handleChange" :min="1" ></el-input-number>
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
    title="剥夺式优先级调度算法"
    :visible.sync="drawer"
    :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">剥夺式优先级调度算法</div>
            <div id="drawer-text-content">  
            
             <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">当一个进程正在处理机上运行时，若有某个更为重要的进程进入就绪队列，则立即暂停正在运行的基础，将处理机分配给更重要的进程。</div>



             <span style="font-weight:bold; display: block;">特点：</span>
                <div style="margin:10px;margin-bottom: 20px;line-height: 1.5;">既可以用于作业调度也可以用于进程调度。该算法中的优先级用于描述作业运行的紧迫程度，因此能更好满足更紧急的任务，适合实时操作系统。</div>
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
            prop="t_prior"
            label="优先数"
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
    name: "HighPrio2",
    data() {
        return {
            form: {
            pcbItem0:[
            {
            pcb_name: 'A',
            pcb_arrive: 0,
            pcb_work: 6,
            
            pcb_start: '',
            pcb_end: '',
            pcb_status: 0,
            pcbGroup: "",
            time_wait1: 0,//等待来临
            time_wait2: 0,//等待上CPU
            prior: 1,
            pcb_truework:''

            },
            {
            pcb_name: 'B',
            pcb_arrive: 2,
            pcb_work: 5,
            
            pcb_start: '',
            pcb_end: '',
            pcb_status: 0,
            pcbGroup: "",
            time_wait1: 0,
            time_wait2: 0,
            prior: 5,
            pcb_truework:''
            
            },
            {
            pcb_name: 'C',
            pcb_arrive: 8,
            pcb_work:10,

            pcb_start: '',
            pcb_end: '',
            pcb_status: 0,
            pcbGroup: "",
            time_wait1: 0,
            time_wait2: 0,
            prior: 3,
            pcb_truework:''
            },
            {
            pcb_name: 'D',
            pcb_arrive: 10,
            pcb_work:4,
            pcb_start: '',
            pcb_end: '',
            pcb_status: 0,
            pcbGroup: "",
            time_wait1: 0,
            time_wait2: 0,
            prior: 2,
            pcb_truework:''
            }
        ]
            },

        pcbItem: [],
        timeNow: 0,
        pcbTable: [],
        tips_text: '',
        linetext: '',
        show: false,
        wait_line: [],//就绪队列的进程名，用于转换为文字
        layer: "",
        pcbSortLine: [],
        beginPos: 0,
            playbackRate: 1,
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
        deleteItem(index) {
            this.form.pcbItem0.splice(index, 1);
            console.log(this.form.pcbItem0, "删除");
        },
        //暂定
        addItem() {
            /**新建一个pcb,默认值全空*/
            this.form.pcbItem0.push({
                pcb_name: '',
                pcb_arrive: '',
                pcb_work: '',
                pcb_start: '',
                pcb_end: '',
                pcb_status:0,
                pcbGroup: "",
                time_wait1: 0,
                time_wait2: 0,
                prior: 0,
                pcb_truework: ''
            });

            console.log(this.form.pcbItem0);
        },

        //暂定
        reset() {
            this.form.pcbItem0 = [
                {
                    pcb_name: '',
                    pcb_arrive: 0,
                    pcb_work: 1,
                    pcb_start: '',
                    pcb_end: '',

                    pcbGroup: "",
                    time_wait1: 0,
                    time_wait2: 0,
                    pcb_status: 0, //0,1未进行完全,2已完成
                    prior: 0,
                    pcb_truework: '',
                    
                }];
            this.pcbItem = [];
            this.layer.removeAllChildren();
            this.pcbTable = [];
            this.pcbSortLine = [];
            this.tips_text = '';
            this.beginPos = 0;
            this.playbackRate = 1;
            this.init();

        },
        handleChange(value) {
            /* console.log(value); */
        },
        //表格变色
        tableRowClassName({ row, rowIndex }) {
            if (row.t_week != "") {
                return 'success-row';
            }
            return '';
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
         highLight(){

        var replace_text= ["就绪队列","运行结束","上CPU","剥夺处理机"]
        var color_dict = { "运行结束": "rgb(255, 0, 0)", "上CPU": "rgb(148, 201, 125)" ,"剥夺处理机":"rgb(255, 0, 0)","就绪队列":"rgb(52, 195, 213)"}
        for(var i=0;i<replace_text.length;i++){
            var color_temp = color_dict[replace_text[i]];
            var replaceString = '<span style="color: ' + color_temp + ';">' + replace_text[i] + "</span>";
            this.tips_text = this.tips_text.replace(RegExp(replace_text[i],'g') ,replaceString);
        }   
        },
        startAnimate(i) {
        const timeline = this.layer.timeline;
        const pcbGroup = this.pcbSortLine[i].pcbGroup;
    
        const pcb = pcbGroup.childNodes[0];//rect对象
        this.layer.append(pcbGroup);//group对象


        var wait_name = pcbGroup.childNodes[1].text;
            
        console.log('delay' + this.pcbSortLine[i].time_wait1 + this.pcbSortLine[i].time_wait2);

        //-------------进入就绪队列
        pcbGroup.animate([
            { x: -25, y: 0},
            { x: 335, y: 0}  //360-25
        ], { 

            delay:this.pcbSortLine[i].time_wait1,
            duration: 1000,
            iterations: 1,
            fill: 'forwards',
            

        });

        timeline.setTimeout(() => {
            this.wait_line.push(wait_name);
                    this.tips_text+="时刻"+this.pcbSortLine[i].pcb_arrive+":进程【"+wait_name+"】到达就绪队列"+'<br/>'
            this.linetext = "就绪队列：" + this.wait_line.toString();
             this.highLight();
        }, this.pcbSortLine[i].time_wait1);

        console.log(i+'到就绪队列的时间：' + this.pcbSortLine[i].time_wait1);

        //-----------------------上CPU，在为了复现过程，都要在就绪队列停2s
            pcb.animate([
                    {fillColor: this.GLOBAL.COLOR_PCB_READY},
                    {fillColor: this.GLOBAL.COLOR_PCB_RUN},
                    
                ], {
                    delay:this.pcbSortLine[i].time_wait2+2000,
                    duration: 1000,
                    iterations: 1,
                    fill: 'forwards',
            });

            pcbGroup.animate([
                {x: 335},
                {x: 600},  
            ], {
                delay:this.pcbSortLine[i].time_wait2+2000,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
            });


        timeline.setTimeout(() => {
            this.removeWaitName(wait_name);
                this.tips_text+="时刻"+this.pcbSortLine[i].pcb_start+":进程【"+wait_name+"】上CPU"+'<br/>'
            this.linetext = "就绪队列：" + this.wait_line.toString();
                 this.highLight();
            },this.pcbSortLine[i].time_wait2+2000);
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
                this.tips_text += "时刻" + this.pcbSortLine[i].pcb_end + ":进程【" + wait_name + "】被剥夺处理机" + '<br/>'

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
            },this.pcbSortLine[i].time_wait2 + 2000 + this.pcbSortLine[i].pcb_truework * 1000+3000); //3s后消失掉
    

            
        },


        //通过这个this.pcbItem[]，算出所有进程片，并加入this.pcbPieceItems[]
        YF(i) {
        console.log('YF PCBItem下标的：'+i);
        //如果该进程没运行结束或还没开始运行
        if ((this.pcbItem[i].pcb_status == 0) || (this.pcbItem[i].pcb_status == 1))
        {
            var prestatus = this.pcbItem[i].pcb_status;
            this.pcbItem[i].pcbGroup = this.setPCBGroup(i);
            this.pcbItem[i].pcb_start = Math.max(this.timeNow,this.pcbItem[i].pcb_arrive); //上一个进程结束的时间与该进程到达时间之间较大的值
            this.pcbItem[i].pcb_end = this.pcbItem[i].pcb_start + this.pcbItem[i].pcb_truework;
            this.pcbItem[i].pcb_status = 2;//默认不被剥夺，能运行结束

            //判断到达队列的 
            var maxPos=i;//最早到达的未执行的进程的索引值    
            var j;
            var maxLevel=this.pcbItem[i].prior; 
            var nextProcess;
            var over=false;//标志：进程i没有运行完
            //判断进程是否会被【新来的进程】打断
            // 1、不打断：这段时间内没有进程到达
            // 2、不打断：到达的进程不会抢占
            // 3、打断：到达的进程会抢占

            for (j = i; j < this.pcbItem.length; j++) {
                //在执行过程中没有新进程来临
                if (this.pcbItem[j].pcb_arrive > this.pcbItem[i].pcb_start+ this.pcbItem[i].pcb_work) {
                    break;
                }
                if (this.pcbItem[j].pcb_status == 2) {
                    continue; //不考虑已经结束的进程
                }
                else if (this.pcbItem[j].prior > maxLevel) {
                    maxPos = j;
                    if (this.pcbItem[j].pcb_arrive == this.pcbItem[i].pcb_end)//当下个高优先进程到达时，正好运行完当前这个进程，能运行完
                    {
                        over = true;//不会中断

                    }
                    break;//因为按照时间顺序拍过PCBItem了，所以只要找到第一个能抢占的就可
                }
            }
            console.log('替换的是'+maxPos);
            console.log('这时PCBItem是：我就不信找不到最早到的比它优先级高的'); console.log(this.pcbItem);


                if (maxPos != i && over == false) {
                    console.log(this.pcbItem[i].pcb_name + '在' + this.pcbItem[maxPos].pcb_arrive + '时刻会被抢占');
                    nextProcess = this.pcbItem[maxPos];
                    this.pcbItem[i].pcb_end = nextProcess.pcb_arrive;
                    this.pcbItem[i].pcb_truework = this.pcbItem[i].pcb_end - this.pcbItem[i].pcb_start;//该进程在这次YF运行的真实时间
                    this.timeNow = this.pcbItem[i].pcb_end;
                    //获取当前位置，插入该status==1（未完成）进程
                    var item = {
                        pcb_name: this.pcbItem[i].pcb_name,
                        prior: this.pcbItem[i].prior,
                        pcb_work: this.pcbItem[i].pcb_work - this.pcbItem[i].pcb_truework,//还需的CPU时间
                        pcb_truework:this.pcbItem[i].pcb_work - this.pcbItem[i].pcb_truework,//还需的CPU时间
                        pcb_arrive: this.pcbItem[i].pcb_end,
                        time_wait1: 0,
                        time_wait2:0,
                        pcbGroup: "",
                        pcb_status: 1,
                        pcb_start: "",
                        pcb_end: ''
                    }
                    j++;
                    console.log('maxPos!=i,CPU被抢占，newpcb_arrive:' + item.pcb_arrive + '暂且（还要按优先级把同样时间到达的进程排序）插入j+1=' + j+'插入后如下所示');
                    this.pcbItem.splice(j, 0, item);//将未执行完的进程加入列表
                    //这里应该需要排序的...补
                    
                    

                    for (var q = j+1; q < this.pcbItem.length - 1; q++){
                        for (var p = q + 1; p < this.pcbItem.length; p++){

                            if ((this.pcbItem[q].pcb_arrive > this.pcbItem[p].pcb_arrive)||((this.pcbItem[q].pcb_arrive ==this.pcbItem[p].pcb_arrive)&&(this.pcbItem[q].prior < this.pcbItem[p].prior)))
                            {
                                var tmp = this.pcbItem[q];
                                this.pcbItem[q] = this.pcbItem[p];
                                this.pcbItem[p] = tmp;
                            }
                                
                            
                        }
                        
                    }
                    console.log(this.pcbItem);
                    
                } else { //不会抢占
                    //从头开始按照先进先出的顺序找下一个上CPU的进程

                    console.log('在YF'+i+'时不会被抢占,over='+over+'。maxPos='+maxPos);
                   
                    for (var m = 0; m < this.pcbItem.length; m++)
                    {
                        if (this.pcbItem[m].pcb_status != 2)
                        {
                            this.beginPos = m;
                            break;
                        }
                    }
                    
                    console.log('beginPos' + this.beginPos);
                    maxLevel = this.pcbItem[this.beginPos].prior;
                    var nextMaxPos = this.beginPos;
                    for (var k = this.beginPos; k < this.pcbItem.length; k++){
                        if (this.pcbItem[k].pcb_arrive>this.pcbItem[i].pcb_end) {
                            break;//不用再往后找了，肯定都没到呢
                        }
                        if (this.pcbItem[k].pcb_status == 2) continue;
                        if (this.pcbItem[k].prior > maxLevel) {
                            maxLevel = this.pcbItem[k].prior;
                            nextMaxPos = k;//确定下一个进程的索引位置
                            
                        }
                    }
                    maxPos = nextMaxPos;
                    nextProcess = this.pcbItem[maxPos];
                    // this.pcbItem[i].pcb_end = this.timeNow + pcb_work; 最开始就赋值了

                    this.timeNow = this.pcbItem[i].pcb_end;//该进程完成后的时间

                    
            }
            //_______________________________________________________________________________________________________________________________
            //计算动画所需要的等待时间
            if (i == 0) {//第一个进程直接上，不停
                this.pcbItem[i].time_wait1 = 0;
                this.pcbItem[i].time_wait2 = 0;
            }
            else
            {
                const preProcess = this.pcbSortLine[this.pcbSortLine.length - 1];
                this.pcbItem[i].time_wait1 = (this.pcbItem[i].pcb_arrive - this.pcbItem[0].pcb_arrive) * 1000;

                /*var addtime = 0;
                for (var a = 0; a < i - 1; a++){
                    console.log('sortpcbline')
                    console.log(this.pcbSortLine[a])
                    if (this.pcbSortLine[a].pcb_truework != this.pcbSortLine[a].pcb_work) {
                        
                        addtime += 4000;
                   } 
                }
*/

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

              /*  this.pcbItem[i].time_wait1 += addtime;
                this.pcbItem[i].time_wait2 += addtime;*/




            }
            const pcbInfo = {
                name: this.pcbItem[i].pcb_name,
                t_prior: this.pcbItem[i].prior,
                t_arr: this.pcbItem[i].pcb_arrive,
                t_work: this.pcbItem[i].pcb_truework,
                t_start: this.pcbItem[i].pcb_start,
                t_end: this.pcbItem[i].pcb_end,
                t_week: "",
                t_weightweek: "",
                t_over:"×" 
            }
            //判断该进程是否运行完成,完成的进程则显示周转和带权周转
            if((this.pcbItem[i].pcb_work - this.pcbItem[i].pcb_truework)==0){//计算周转和带权周转
           
               

                var initWork = 0;
                var initArrive = 0;
                for (var k = 0; k < this.form.pcbItem0.length; k++){
                if (this.form.pcbItem0[k].pcb_name == this.pcbItem[i].pcb_name) {
                    initWork = this.form.pcbItem0[k].pcb_work;
                    initArrive = this.form.pcbItem0[k].pcb_arrive;
                    break;
                }
                }
                pcbInfo.t_week = this.pcbItem[i].pcb_end -initArrive;
                var number=pcbInfo.t_week/initWork ;
                pcbInfo.t_weightweek = number.toFixed(2);
                pcbInfo.t_over="√"
            }
            this.pcbTable.push(pcbInfo);
            this.pcbSortLine.push(this.pcbItem[i]);
            if (this.pcbSortLine.length >= this.pcbItem.length) {
                return;
            }   
            this.YF(maxPos);

            }


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

            const rect = new Rect({
                normalize: true,
                pos: [cpupos, init_top + 50],
                anchor: [0, 0],
                size: [100, 100],
                lineWidth: 2,
                strokeColor: 'black',
            });
            this.layer.append(rect);

            //cpu文字说明
            const cputext = new Label('CPU');
            cputext.attr({
                pos: [cpupos - 25, 60 + init_top],
                anchor: [0, 0],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '25px "黑体"',
                lineHeight: 100,
                textAlign: 'center',
            });
            this.layer.append(cputext);


            //就绪队列
            const line = new Sprite({
                normalize: true,
                pos: [50, 25 + init_top],
                anchor: [0, 0],
                size: [360, 50],
                bgcolor: 'white',
                borderWidth: 2,
            });
            this.layer.append(line);

        },

         removeWaitName(str) {
        for(let i=0;i<this.wait_line.length;i++){
                    if(this.wait_line[i] == str){
                        
                        this.wait_line.splice(i, 1); 
                    console.log('找' + str + "剩" + this.wait_line)
                        
                        break;
                    }
                }
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

        async submitForm() {

            this.pcbItem = this.form.pcbItem0.concat([]);//深拷贝，返回一个新数组
            console.log(this.pcbItem);

            //按照先来后到排个序，才合理。
            for (let i = 0; i < this.pcbItem.length - 1; i++) {
                for (let j = i + 1; j < this.pcbItem.length; j++) {
                    if (this.pcbItem[i].pcb_arrive > this.pcbItem[j].pcb_arrive) {
                        var tmp = this.pcbItem[i];
                        this.pcbItem[i] = this.pcbItem[j];
                        this.pcbItem[j] = tmp;
                    }

                    //如果到达时间相同，按照优先级排个序，才能选出第一个上CPU的~
                    else if ((this.pcbItem[i].pcb_arrive == this.pcbItem[j].pcb_arrive) && (this.pcbItem[i].prior < this.pcbItem[j].prior))
                    {
                        var tmp = this.pcbItem[i];
                        this.pcbItem[i] = this.pcbItem[j];
                        this.pcbItem[j] = tmp;
                    }
                }
            }
            for (let i = 0; i < this.pcbItem.length; i++)
            {
                //初始化真实工作时间为所需的work时间
                this.pcbItem[i].pcb_truework = this.pcbItem[i].pcb_work;
            }
            //将第一个上CPU的进程的到达时间赋值给当前时间
            this.timeNow = this.pcbItem[0].pcb_arrive;
            await this.YF(0);
          
         /*    console.log('不排序：');
            console.log(this.pcbSortLine);

           var last_time1 = this.pcbSortLine[0].time_wait1;
            var add_time = 0;
            this.pcbSortLine.sort((a, b) => { return a.time_wait1 - b.time_wait1 });
            console.log('第一次排序后：');
            console.log(this.pcbSortLine);

            for (var s = 0; s < this.pcbSortLine.length; s++){
                var time1 = this.pcbSortLine[s].time_wait1;
                if (last_time1 == this.pcbSortLine[s].time_wait1)
                {
                    this.pcbSortLine[s].time_wait1+=add_time;
                }
                else
                {
                    add_time += ERROR;
                    this.pcbSortLine[s].time_wait1 += add_time;
                }
                last_time1 = time1;
            }
            
            this.pcbSortLine.sort((a, b) => { return a.time_wait2 - b.time_wait2 });
            console.log('第二次排序后：');
            console.log(this.pcbSortLine);*/

        


            for (var i = 0; i < this.pcbSortLine.length; i++) {

                this.startAnimate(i);
            }
        },
        
    },
    mounted() {
    this.init();
    },
}
</script>