<template>


<div id="outer">
 <el-card class="box-card">
  <div class="setting">
<el-scrollbar class="scrollbar">
    <!--标题-->
    <div class="box-card-setting-title">
            <div class="box-card-setting-title-1">空闲分区分配算法</div>
            <div class="box-card-setting-title-2">最坏适应（WF）算法</div> 
    </div>

    <!--第一个表格用来记录各个空闲分区块的大小-->
    <label>空闲分区信息：</label>
    <el-form ref="form1" :inline="true" :model="partition" >
    <div v-for="(item, index) in partition.partition_input" :key="index">
        <el-form-item label-width="50px">第{{index}}分区</el-form-item>

        <el-form-item label-width="120px" label="分区大小" :prop="'partition_input.' + index + '.size'"
        :rules="[{required: true, message: '分区大小不能为空', trigger: 'blur'},]">
        <el-input-number size="mini" v-model="item.size" @change="handleChange" :min="1" :max="10000" ></el-input-number>
        </el-form-item>

        <el-form-item>
        <el-button size="mini" v-if="index+1 == partition.partition_input.length" @click="addItem1" type="primary">增加</el-button>
        <el-button size="mini" v-if="index !== 0" @click="deleteItem1(index)" type="danger">删除</el-button>
        </el-form-item>
    </div>
    
   
    <el-button size="mini"  :disabled = "isSubmit1==true" @click="onSubmit1()" type="primary" >提交</el-button>
    <el-button size="mini"  @click="reset1()" type="danger">重置</el-button>
    </el-form>
    
    <el-divider></el-divider>
    <!--第二个表格用来记录各个进程的信息-->
    <label>进程信息：</label>
    <el-form ref="form2" :inline="true" :model="process" >
    <div v-for="(item, index) in process.process_input" :key="index">

        <el-form-item label-width="50px">{{index}}</el-form-item>

         <el-form-item label-width="100px" label="进程名称" :prop="'process_input.' + index + '.pcb_name'"
        :rules="{required: true, message: '名称不能为空', trigger: 'blur'}">
          <el-input size="mini" v-model="item.pcb_name"></el-input>
        </el-form-item>

        <el-form-item label-width="120px" label="需要分区大小" :prop="'process_input.' + index + '.pcb_need_size'"
        :rules="[{required: true, message: '分区大小不能为空', trigger: 'blur'},]">
          <el-input-number size="mini" v-model="item.pcb_need_size" @change="handleChange" :min="1" :max="10000" ></el-input-number>
        </el-form-item>

        <el-form-item>
        <el-button size="mini" v-if="index+1 == process.process_input.length" @click="addItem2" type="primary">增加</el-button>
        <el-button size="mini" v-if="index !== 0" @click="deleteItem2(index)" type="danger">删除</el-button>
        </el-form-item>
    </div>
  
    </el-form>


</el-scrollbar>
  </div>
    <el-divider></el-divider>
    
    <el-drawer
      title="最坏适应算法"
      :visible.sync="drawer"
      :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">最坏适应算法</div>
            <div id="drawer-text-content">     
                <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;">优先使用更大的分区，以防止产生太小的难以利用的碎片。</div>
            
                <span style="font-weight:bold; margin-bottom:7px; display: block;">分区排列顺序：</span>
                <div style="margin:10px;margin-bottom: 20px;">空闲分区以容量递减次序排列。</div>

                <span style="font-weight:bold; margin-bottom:7px; display: block;">优点：</span>
                <div style="margin:10px;margin-bottom: 20px;">可以减少难以利用的小碎片。</div>

                <span style="font-weight:bold; margin-bottom:7px; display: block;">缺点：</span>
                <div style="margin:10px;margin-bottom: 20px;">大分区容易被用完，不利于大进程；算法开销大，分配、回收分区后需要对空闲分区队列重新排序。</div>


            </div>
        
        </div>
    </el-drawer>

     <div id="option">
     
        <el-button size="mini"  @click="onSubmit2()" type="primary" :disabled = "isSubmit1==false">提交</el-button>
        <el-button size="mini"  @click="reset2()" type="danger">重置</el-button>
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


<div id="canvas" style="width: 1300px; height: 400px; /*background: #dff0df;*/ position:relative">


<div id="adaptive" style="width: 1300px; height: 400px; background: #fff; position:absolute; 
margin-left: 84px;"></div>

</div>

<el-divider></el-divider>


  <div id="info"  align="center">
    <el-table
            :default-sort = "{prop: 't_partition'}"
            
            :data="allocTables"
            style="width: 720px"
            :row-class-name="tableRowClassName">
            <el-table-column
            prop="t_partition"
            label="空闲分区号"
            width="180">
            </el-table-column>
            <el-table-column
            prop="t_pcb"
            label="进程名"
            width="180">
            </el-table-column>
           
            <el-table-column
            prop="t_occupied"  width="180"
            label="进程所需空间">
            </el-table-column>    
           
           <el-table-column
            prop="t_rest"
            label="空闲分区的剩余空间"
            width="180">
            </el-table-column>
           
        </el-table>

  </div>




</div>

</template>

<script>



import * as spritejs from 'spritejs';


const { Scene, Sprite, Rect, Label, Group, Path, Gradient } = spritejs;

const FULL_GROUP_NAME='groupFull'
const EMPTY_GROUP_NAME='groupEmpty'

export default {
    name: "WF",

        data() {
        return {
        partition: {
                partition_input: [
                    {
                        size: 90,
                        restSpace: 90,
                        pid:[],
                    },
                    {
                        size: 50,
                        restSpace: 50,
                        pid:[],
                    },
                    {
                        size: 70,
                        restSpace: 70,
                        pid:[],
                    },
                    {
                        size: 90,
                        restSpace: 90,
                        pid:[],
                    }
                 ]
            },
        process:{
            process_input: [
                {
                    pcb_name: "work0",    
                    pcb_need_size:"50",
                },
                {
                    pcb_name: "work1",    
                    pcb_need_size:"70",
                },
                {
                    pcb_name: "work2",    
                    pcb_need_size:"50",
                },
                {
                    pcb_name: "work3",    
                    pcb_need_size:"100",
                },
                ]
        },
        partitionArray: [],
        arrowArray:[],
        processArray: [],
        animate_partition_group:[],//存分区的动画元素
        layer: "",
        playbackRate:1.0,
        next_partition_pos:[60,180],//记录分区在画布上的位置
        isSubmit1:false,/*第一个表格是否提交，如果不提交，第二个提交按钮应该为禁用状态 */
        timeNow: 0,
        pause:false,
        show: false,
        allocTables: [],
        partitionGroup: "",
        //  timeArr: [2000, 18000, 28000,38000,48000],
        timeArr: [],
        drawer:false

        
        
        



        }
    },
    methods: {
  /*      getSpanArr(data) {　
     console.log(data)
          for (var i = 0; i < data.length; i++) {
            if (i === 0) {
              this.spanArr.push(1);
              this.pos = 0
            } else {
              // 判断当前元素与上一个元素是否相同
            if (data[i].t_partition === data[i - 1].t_partition) {
                this.spanArr[this.pos] += 1;
                this.spanArr.push(0);
              } else {
                this.spanArr.push(1);
                this.pos = i;
              }
            }
        }
    },

     objectSpanMethod({ row, column, rowIndex, columnIndex }) {

          if (columnIndex === 0) {
            const _row = this.spanArr[rowIndex];
            const _col = _row > 0 ? 1 : 0;
            return {
                  rowspan: _row,
                  colspan: _col
            }
          }
    },*/

    tableRowClassName({row, rowIndex}) {
      if (row.t_first == 'yes') {
      return 'success-row';
      }
     return '';


        },
        handleChange(value) {
            console.log(value);
        },
        addItem1() {
        
            this.partition.partition_input.push({
            pid:[],
            size: 10,
            restSpace:10
          });
        },
        deleteItem1(index) {
             this.partition.partition_input.splice(index, 1);
            console.log(this.partition.partition_input, "删除空闲分区");
        },

        
      addItem2() {
          this.process.process_input.push({
             pcb_name: "",    
             pcb_need_size:0,
          });
        },
        deleteItem2(index) {
             this.process.process_input.splice(index, 1);
            console.log(this.process.process_input, "删除进程");
        },
        reset1() {
            this.partition.partition_input=[{ size:"",pid:[]}]
        },
        reset2() {
            this.timeArr = [];
            this.timeNow = 0;
            this.process.process_input=[{pcb_need_size:"",pcb_name:""}]
            this.partition.partition_input = [{ size: "", pid: [] }]
            this.allocTables = [];
            this.next_partition_pos = [60, 180];
            this.isSubmit1 = false;
            this.layer.removeAllChildren();
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
       
        //寻找有无合适该进程的分区下标
        findPartitionPos(i) {
            var partitionPos = null;
          
            for (var j = 0; j < this.partitionArray.length; j++){
                if (this.partitionArray[j].restSpace >= this.processArray[i].pcb_need_size) {
                    partitionPos = j;
                    break;
                }
            }
            return partitionPos;
        },
        calTimeArr() {
            this.timeArr[0] = 2000;
            //首先计算，预测进程动画的每个步骤花费时间
            //var processArr1 = this.process.process_input.concat([]);
            //var partition1 = this.partition.partition_input.concat([]);
            let processArr1 =JSON.parse(JSON.stringify(this.process.process_input) )//slice();


            let partition1 = JSON.parse(JSON.stringify(this.partition.partition_input))  ;

         

            for (var i = 0; i < processArr1.length;i++)
            {

                var res = 3000; //前一个排序2000，进程从底部出来1000

                var find = 0;

                
                partition1.sort((a, b) => { return b.restSpace-a.restSpace });//升序排一下

                if (partition1[0].restSpace != 0) {
                    find += 2000;
                     if (i == 3)
                        {
                            console.log('第一为不空，等待2s');
                        }
                }
                if (partition1[0].restSpace<processArr1[i].pcb_need_size)
                {

                for (var j = 1; j < partition1.length; j++)
                {
                    if (partition1[j].restSpace == 0) {
                        find += 1000;

                        if (i == 3)
                        {
                            console.log('为空：滑动+1');
                        }
                    }
                    else
                    {
                        find += 3000;
                          if (i == 3)
                        {
                            console.log('不为空：滑动+比较+3');
                        }
                    }
                    
                    if (partition1[j].restSpace >= processArr1[i].pcb_need_size) {
                        partition1[j].restSpace -= processArr1[i].pcb_need_size;
                        find += 3000;
                       
                        if (i == 3)
                        {
                            console.log('找到并上去+3');
                        }
                        break;
                    }
                }
                }
                else
                {
                    find += 3000;
                }
              

                
                res += find;
                if (i == 3) {
                    console.log(find);
                    console.log(res);
                }
                this.timeArr.push(res);
                this.timeArr[i+1] += this.timeArr[i];
            }

        },
        allocation(i, partitionPos) {//分配空闲分区给进程，并修改空闲分区信息
            if (partitionPos == null) {
                return 0; //不存在合适分区的情况
            } else if (this.partitionArray[partitionPos].restSpace > this.processArray[i].pcb_need_size) {
                //分割

                this.partitionArray[partitionPos].pid.push(i);
                this.partitionArray[partitionPos].restSpace -= this.processArray[i].pcb_need_size;
                let allocInfo = {
                    t_partition: this.partitionArray[partitionPos].index,
                    t_pcb: this.processArray[i].pcb_name,
                    t_occupied: this.processArray[i].pcb_need_size,
                    t_rest: this.partitionArray[partitionPos].restSpace,
                    t_first:'no'
                }
                this.allocTables.push(allocInfo);
                return 2;
            } else
            {
                //整块分配
                this.partitionArray[partitionPos].pid.push(i);
                this.partitionArray[partitionPos].restSpace = 0;

                 let allocInfo = {
                    t_partition:  this.partitionArray[partitionPos].index,
                    t_pcb: this.processArray[i].pcb_name,
                    t_occupied: this.processArray[i].pcb_need_size,
                    t_rest:  this.partitionArray[partitionPos].restSpace
                }
                this.allocTables.push(allocInfo);
                return 1;
            }

        },
        drawFullPartition(partition,posXY) {
        const area =  new Rect({
            name:"area",
            normalize: true,
            pos: [50, 20],
            anchor: [0, 0],
            size: [100, 40],
            fillColor: this.GLOBAL.COLOR_FULL,
            lineWidth: 2,
            strokeColor: 'black',
            zIndex:10,
          });
        
          const areaSize = new Label(partition.size.toString()+"K");
          areaSize.attr({
                name:"areaSize",
                pos: [50, 20],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
          });
          const pcbGroup = new Group();
            pcbGroup.attr({
                name:EMPTY_GROUP_NAME,
                size: [100, 60],
                pos:posXY,
                // bgcolor: '#cec',
            });
            pcbGroup.append(area);
            pcbGroup.append(areaSize);
          // this.animate_partition_group.push(pcbGroup);
          return pcbGroup;
        },
        // 空闲分区在画布中的横坐标,进程pos,空闲分区pos
        changePartition(nextPos,i,j,delay0) {
          const text=this.partitionArray[j].restSpace.toString();
          this.partitionArray[j].group.children[1].animate([//修改分区剩余空闲区域
            {text: ""},
            {text:text+"K"},
            ], {
              delay:delay0,
              duration: 0,
              iterations: 1,
              fill: 'forwards',
            });
          var newPartition={
            size:this.processArray[i].pcb_need_size,
            free:0,
            pos:"",
            pid:"",
            group:"",
          }
          newPartition.group=this.drawFullPartition(newPartition,[nextPos,-200]);//绘制得到被分离的占满分区
          // this.partition_line[j].full_group_line.push
          this.layer.append(newPartition.group)
          var height=this.partitionArray[j].pid.length * 40;
          newPartition.group.animate([
            {x: nextPos,y: this.next_partition_pos[1]},
            // {x: nextPos,y: this.next_partition_pos[1]-height},
            {x: nextPos,y: this.next_partition_pos[1]-40},
            ], {
              delay:delay0,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });
            this.timeNow += 500;
            delay0 += 500;
          this.layer.timeline.setTimeout(() => {
            newPartition.group.remove()
          }, delay0 + 1000);
            delay0 += 1000;
          this.timeNow+=1000;
          console.log('加完分区了')
        },
        changeFillColor(color,patitionPos,delay0) {
        if(color == this.GLOBAL.COLOR_EMPTY){
            this.partitionArray[patitionPos].group.children[0].animate([
              {fillColor: this.GLOBAL.COLOR_EMPTY},
              {fillColor: this.GLOBAL.COLOR_EMPTY},
            ], {
              delay:delay0,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });
        } else {
            this.partitionArray[patitionPos].group.children[0].attributes.name=FULL_GROUP_NAME;
            this.partitionArray[patitionPos].group.children[0].animate([
              {fillColor: this.GLOBAL.COLOR_EMPTY},
             { fillColor: this.GLOBAL.COLOR_FULL},
              
            ], {
              delay:delay0,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });

             this.partitionArray[patitionPos].group.children[1].animate([
            {text:""},{text:"0K"}
              
            ], {
              delay:delay0,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });

            }
            
          console.log('改完色了')
        },
        changeArrow(j,delay0) {

            //j==this.animate_partition_group.length - 1时本来就不需要改变后面的箭头但是前面的箭头会改
            if (j < this.animate_partition_group.length - 1) {

                if (this.arrowArray[j] != null) {//有指向下一个的箭头
                 this.arrowArray[j].animate([//将箭头透明度设置为0，达到隐形的效果
                    {opacity:1},
                    {opacity:0},
                ], {
                    delay:delay0+500,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                });
                    this.timeNow += 500;
                    delay0 += 500;
                    this.arrowArray[j] = null;//表示箭头数组中的此空闲块对应的箭头已无
                }
            }
            var first = -1;
            for (var k = 0; k < this.animate_partition_group.length - 1; k++){

                //找到第一个有指向下一块的空闲块
                if (this.arrowArray[k] != null) {
                    first = k;
                    break;
                }
            }
            if (first == -1||j==0) {
                console.log('当这个分区被占用后，所有分区都已被占用了，没有前一个箭头！');
                return;
            }

            //判断前面哪个箭头连的哪个分区
            var preJ=this.findPreLink(j);
            var nextJ = this.findNextLink(j);
            if (preJ == null) {
                console.log('我前面就没有，所以不用改~')
                return;
            }
            if (nextJ == null) {
                //取消前一个箭头的透明度
                console.log('nextK == null!!!!!!!!!!')
                   this.arrowArray[preJ].animate([//将箭头透明度设置为0，达到隐形的效果
                    {opacity:1},
                    {opacity:0},
                  ], {
                    delay:delay0+500,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                  });
                this.timeNow += 500;
                delay0 += 500;
            } else //在preJ和nextJ间画新箭头~
            {
                this.arrowArray[preJ].animate([
                {d: ''},
                {d: this.drawNewArrow(preJ,nextJ)},
                ], {
                delay:delay0,
                duration: 0,
                iterations: 1,
                fill: 'forwards',
                });
            }



            
        },
        drawNewArrow(preJ, nextJ) {
            
            var len = (nextJ - preJ-1) * 300;
            console.log('len=' + len);
            var  d= 'md0,d0l d50,d0l d0,d-100l d'+len+',d0l d0,d100l d50,d0l d-35,d-20m d35,d20l d-35,d20m d35,d20'
          return d;
            
        },
        findPreLink(j) {
            console.log(j+'前面的空闲区都是------------');
            for (var k = j - 1; k >= 0; k--){
                console.log(this.partitionArray[k].restSpace);
                if (this.partitionArray[k].restSpace > 0) {
                    return k;
                }
            }
            console.log('------------');
            return null;
        },
        findNextLink(j) {
             for (var k = j+1; k<this.partitionArray.length; k++){
                if (this.partitionArray[k].restSpace > 0) {
                    return k;
                }
            }
            return null;
        },
         async onSubmit1() {
            this.partitionArray = this.partition.partition_input.concat([]);

            this.partitionGroup = new Group();
            this.partitionGroup.attr({
                pos: [0,0],
            });

            for (var i = 0; i < this.partitionArray.length; i++) { 

                //下面这仨是增加的属性
                this.partitionArray[i].index = i;//确定下标号，虽然这个不用排序，但是可作为固定标记。
                this.partitionArray[i].restSpace = this.partitionArray[i].size;
                this.partitionArray[i].group = this.drawPartition(this.partitionArray[i]);
             
                this.partitionArray[i].group.attr('opacity',1);
                this.layer.append(this.partitionArray[i].group)
                if(i!=this.partitionArray.length-1){
                    const arrow=this.drawArrow();
                    this.arrowArray.push(arrow);
                    this.layer.append(arrow);
                }    
                this.next_partition_pos[0] += 150;



                 let allocInfo = {
                    t_partition: i,
                    t_pcb: '分区尚未存在进程',
                    t_occupied: '--------',
                     t_rest: this.partitionArray[i].restSpace,
                    t_first:'yes'
                }
                this.allocTables.push(allocInfo);
            }
            this.isSubmit1=true;
             this.childnum_in_layer = this.partitionArray.length;

        },
        reSortLine(tmptime0) {//对partitionArray重新排序
          console.log(this.timeNow);
          const comment = new Label("将分区从大到小重新排序后:");
         comment.attr({
                name:"comment",
                pos: [200, 130],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '25px "黑体"',
                zIndex:100,
                opacity:0
          });
          this.layer.append(comment);
          comment.animate([
              {opacity: 0},
              {opacity: 1},
            ], {
              delay:1000,
              duration: 0,
              iterations: 1,
              fill: 'forwards',
            });
          this.layer.timeline.setTimeout(() => {
            comment.remove();
          }, tmptime0 + 2000)
          
     
            this.partitionArray.sort((a, b) => { return b.restSpace - a.restSpace })


            this.animate_partition_group = [];
            this.arrowArray = [];
            for (var i = 0; i < this.partitionArray.length; i++) {

                
                this.partitionArray[i].group = this.drawPartition(this.partitionArray[i]);
           
                this.animate_partition_group.push(this.partitionArray[i].group);
                //   console.log(this.partitionGroup);

                this.layer.append(this.partitionArray[i].group);
                this.partitionArray[i].group.animate([
                    { opacity: 0 },
                    { opacity: 1 },
                ], {
                    delay: 500,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                });
                if (this.partitionArray[i].restSpace==0) { //变红
                     this.partitionArray[i].group.children[0].animate([
                    {fillColor: this.GLOBAL.COLOR_EMPTY},
                    { fillColor: this.GLOBAL.COLOR_FULL},
                    
                    ], {
                    delay:500,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                    });
                }
                var arrow;
                if (i != this.partitionArray.length-1&& this.partitionArray[i].restSpace!=0)
                { 
                    arrow= this.drawArrow();
                    this.arrowArray.push(arrow);
                    this.layer.appendChild(arrow);
                    arrow.animate([
                    { opacity: 0 },
                    { opacity: 1 },
                ], {
                    delay: 500,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                    });
                } else if (i != this.partitionArray.length-1&& this.partitionArray[i].restSpace==0) {
                      this.arrowArray.push(null);
                }
               
               

                this.next_partition_pos[0] += 150;
            }
            this.timeNow += 1000;
            tmptime0 += 2000;


        },
        async onSubmit2() {

          /*    this.processArray = this.process.process_input.concat([]);
      for (let i = 0; i < 4; i++){
                let tmptime=this.timeNow;
                    this.layer.timeline.setTimeout(() => {
                    this.layer.removeAllChildren();
                    this.next_partition_pos= [60, 230]
                    this.reSortLine();
                    const work = this.drawProcess(this.processArray[i]);
                    this.layer.append(work);
                work.animate([
              {x: 200,y: 500},
              {x: 200,y: this.next_partition_pos[1]+50},
            ], {
              delay:tmptime,
              duration: 1000,
              iterations: 1,
              fill: 'forwards',
            });
                this.timeNow += 1000;
                tmptime += 1000;
                this.partitionArray[3].restSpace -= 20; 
           work.animate([
                  {x: nextPos,y: this.next_partition_pos[1]+50},
                  {x: nextPos,y: this.next_partition_pos[1]},
                  ], {
                    delay:tmptime+2000,
                    duration: 1000,
                    iterations: 1,
                    fill: 'forwards',
                  });
                        this.timeNow += 3000;
                 tmptime += 3000;
                 this.layer.timeline.setTimeout(() => {
                      work.remove();
                  },tmptime+500);
                this.timeNow += 500;
                tmptime += 500;
                 var newPartition={
                            size:this.processArray[i].pcb_need_size,
                            free:0,
                            pos:"",
                            pid:"",
                            group:"",
                        }
          newPartition.group=this.drawFullPartition(newPartition,[nextPos,-200]);//绘制得到被分离的占满分区
         
          this.layer.append(newPartition.group)
            newPartition.group.animate([
            {x: nextPos,y: this.next_partition_pos[1]},
            // {x: nextPos,y: this.next_partition_pos[1]-height},
            {x: nextPos,y: this.next_partition_pos[1]-40},
            ], {
              delay:tmptime,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });
                        this.timeNow += 500;
                tmptime+=500

            this.layer.timeline.setTimeout(() => {
                newPartition.group.remove();
            }, tmptime + 1000);
                this.timeNow += 500

            }, 10000*i);
              
            }*/
                
            this.processArray = this.process.process_input.concat([]);

        console.log('=========timearr');
           
            this.calTimeArr();
            console.log(this.timeArr);
            console.log(this.partitionArray);


            for (let i = 0; i < this.processArray.length; i++) {
               let tmptime=this.timeNow;
                this.layer.timeline.setTimeout(() => {
                    console.log(this.layer.children);
                    this.layer.removeAllChildren();
                    console.log(this.layer.children);
                    this.next_partition_pos = [60, 180]
                    this.reSortLine(tmptime);



                     //计算空闲分区的位置
                 const patitionPos = this.findPartitionPos(i);
                
                const resultType = this.allocation(i, patitionPos);
                   //生成进程
                const pcb=this.drawProcess(this.processArray[i]);
                this.layer.append(pcb);
                var nextPos = 60;
                pcb.animate([
                {x: nextPos,y: 500},
                {x: nextPos,y: this.next_partition_pos[1]+50},
                ], {
                delay:tmptime,
                duration: 1000,
                iterations: 1,
                fill: 'forwards',
                });
                    this.timeNow += 1000;
                    tmptime += 1000;


                    for (let j = 0; j < this.partitionArray.length; j++){
                        if (resultType == 0)
                        {
                            break;
                        }
                        if (j == patitionPos) {//找到的

                            pcb.animate([
                            {x: nextPos,y: this.next_partition_pos[1]+50},
                            {x: nextPos,y: this.next_partition_pos[1]},
                            ], {
                                delay:tmptime+2000,
                                duration: 1000,
                                iterations: 1,
                                fill: 'forwards',
                            });

                            this.timeNow += 3000;
                            tmptime += 3000;
                                         //消失
                    this.layer.timeline.setTimeout(() => {
                      pcb.remove();
                    },tmptime +500);

                    this.timeNow += 500;
                    tmptime+=500

                    
                    if (resultType == 1) {
                        //空闲分区被占满
                        //变红
                        
                        this.changeFillColor(this.GLOBAL.COLOR_FULL, j,tmptime);
                        //改变箭头颜色
                        this.changeArrow(j,tmptime);
                    } else {
                        //没占满
                        //去红，留白
                        this.changePartition(nextPos,i,j,tmptime);
                        this.changeFillColor(this.GLOBAL.COLOR_EMPTY,j,tmptime);

                        }
                            break;


                        } else {

                            nextPos += 150;
                            //直接划走
                            if (this.partitionArray[j].restSpace == 0) {
                                pcb.animate([
                                {x: nextPos-150,y: this.next_partition_pos[1]+50},
                                {x: nextPos,y: this.next_partition_pos[1]+50},
                                ], {
                                    delay:tmptime,
                                    duration: 1000,
                                    //duration:0,
                                    iterations: 1,
                                    fill: 'forwards',
                                });
                                this.timeNow += 1000;
                                tmptime += 1000;
                            } else {

                                  pcb.animate([
                                {x: nextPos-150,y: this.next_partition_pos[1]+50},
                                {x: nextPos,y: this.next_partition_pos[1]+50},
                                ], {
                                    delay:tmptime+2000,
                                    duration: 1000,
                                    //duration:0,
                                    iterations: 1,
                                    fill: 'forwards',
                                });
                                tmptime += 3000;
                                this.timeNow += 3000;

                        }
                        }
                     

                            
                    }
                    if (resultType == 0) {
                            this.layer.timeline.setTimeout(() => {
                            pcb.remove();
                            }, tmptime + 2000);
                          tmptime += 2000;
                          this.timeNow+=2000;
                        }
                       
                    
                    
                }, this.timeArr[i]);


            }
            



        
           
          
           


           
          
            
            
        },


            drawProcess(process) {
            const pcb =  new Rect({
            name:"pcb",
            normalize: true,
            pos: [50, 20],
            anchor: [0, 0],
            size: [80, 40],
            fillColor: this.GLOBAL.COLOR_PROCESS,
            lineWidth: 2,
            strokeColor: 'black',
          });
        
          const needSize = new Label(process.pcb_need_size.toString()+"K");
          needSize.attr({
                name:"needSize",
                pos: [50, 20],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: this.GLOBAL.WORK_FONT,
          });
          const name = new Label(process.pcb_name);
          name.attr({
                name:"name",
                pos: [50, 50],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: this.GLOBAL.WORK_FONT,
          });
          const pcbGroup = new Group();
            pcbGroup.attr({
                pos:[200,600],//藏在画布外面
                size: [100, 100],
                // bgcolor: '#cec',
            });
            pcbGroup.append(pcb);
            pcbGroup.append(needSize);
            pcbGroup.append(name);
          return pcbGroup;
            
        },
        drawArrow() {
            const arrow = new Path();
            arrow.attr({
                name: 'arrow',
                d: 'md0,d0l d100,d0l d-35,d-20m d35,d20l d-35,d20m d35,d20' ,
                 scale:0.5,
              strokeColor: 'black',
              lineWidth: 5,
              pos: [this.next_partition_pos[0]+100,this.next_partition_pos[1]+20],
              zIndex:100,
            })
            return arrow;
            
        },
        drawPartition(partition) {
            const area =  new Rect({
            name:"area",
            normalize: true,
            pos: [50, 20],
            anchor: [0, 0],
            size: [100, 40],
            fillColor: this.GLOBAL.COLOR_EMPTY,
            lineWidth: 2,
            strokeColor: 'black',
            zIndex:10,
            });

             const areaSize = new Label(partition.restSpace.toString()+"K");
          areaSize.attr({
                name:"areaSize",
                pos: [50, 20],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
          });

         const areaNo = new Label(partition.index.toString());
          areaNo.attr({
                name:"areaNo",
                pos: [50, -20],
                anchor: [0.5, 0.5],
                fillColor: this.GLOBAL.COLOR_FONT,
                font: '20px "黑体"',
                zIndex:100,
          });
          const pcbGroup = new Group();
            pcbGroup.attr({
                name:EMPTY_GROUP_NAME,
                size: [100, 60],
                pos: this.next_partition_pos,
                opacity:0
                // bgcolor: '#cec',
            });


            pcbGroup.append(area);
            pcbGroup.append(areaSize);
            pcbGroup.append(areaNo);
          this.animate_partition_group.push(pcbGroup)
          return pcbGroup;
          
        },
        init() {
       const container = document.getElementById("adaptive");
       const scene = new Scene({ // 初始化场景scene，它不是canvas，只是一个用来包裹canvas的div元素
        container,
        width: 1300,
        height: 400,
        mode: 'stickyWidth',
      });
            this.layer = scene.layer();// 创建背景图层layer，这个就是真正意义上的canvas了

        }


        
    },
    
        
    mounted(){
        this.init();

    }
   
}
</script>