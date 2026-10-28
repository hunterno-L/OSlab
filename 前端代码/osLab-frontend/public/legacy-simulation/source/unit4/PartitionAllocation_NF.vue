<template>

<div id="outer">
 <el-card class="box-card">
  <div class="setting">
<el-scrollbar class="scrollbar">
    <!--标题-->
    <div class="box-card-setting-title">
            <div class="box-card-setting-title-1">空闲分区分配算法</div>
            <div class="box-card-setting-title-2">邻近适应（NF）算法</div> 
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

<el-drawer
      title="邻近适应算法"
      :visible.sync="drawer"
      :with-header="false">
        <div id="drawer-text">
            <div id="drawer-text-title">邻近适应算法</div>
            <div id="drawer-text-content">     
                <span style="font-weight:bold; display: block;">算法思想：</span>
                <div style="margin:10px;margin-bottom: 20px;">由首次适应算法演变而来，每次从上次查找结束位置开始查找。</div>
            
                <span style="font-weight:bold; margin-bottom:7px; display: block;">分区排列顺序：</span>
                <div style="margin:10px;margin-bottom: 20px;">空闲分区以地址递增次序排列（可排列成循环链表）。</div>

                <span style="font-weight:bold; margin-bottom:7px; display: block;">优点：</span>
                <div style="margin:10px;margin-bottom: 20px;">不用每次都从低地址的小分区开始检索；算法开销小，回收分区后一般不需要对空闲分区队列重新排序。</div>

                <span style="font-weight:bold; margin-bottom:7px; display: block;">缺点：</span>
                <div style="margin:10px;margin-bottom: 20px;">会使高地址的大分区也被用完。</div>


            </div>
        
        </div>
    </el-drawer>
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
    name: "NF",
    data() {
        return {
           partition: {
                partition_input: [
                    {
                        size: 50,
                        restSpace: 50,
                        pid:[],
                    },
                    {
                        size: 20,
                        restSpace: 20,
                        pid:[],
                    },
                    {
                        size: 70,
                        restSpace: 70,
                        pid:[],
                    },
                    {
                        size: 50,
                        restSpace: 50,
                        pid:[],
                   },
                    {
                        size: 10,
                        restSpace: 10,
                        pid:[],
                   },
                    {
                        size: 90,
                        restSpace: 90,
                        pid:[],
                   },
                    {
                        size: 5,
                        restSpace: 5,
                        pid:[],
                    }
                 ]
            },
        process:{
            process_input: [
                {
                    pcb_name: "work0",    
                    pcb_need_size:"70",
                },
                {
                    pcb_name: "work1",    
                    pcb_need_size:"90",
                },
                {
                    pcb_name: "work2",    
                    pcb_need_size:"5",
                },
                {
                    pcb_name: "work3",    
                    pcb_need_size:"5",
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
            last_index:0,//循环首次适应算法，记录每次查找中断的下标位置
            last_index_nf: 0,
            drawer:false

        
        }
    },
    methods: {

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
            this.last_index=0,//循环首次适应算法，记录每次查找中断的下标位置
            this.last_index_nf=0,
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

        async onSubmit1() {
            this.partitionArray = this.partition.partition_input.concat([]);
            
            for (var i = 0; i < this.partitionArray.length; i++) { 

                //下面这仨是增加的属性
                this.partitionArray[i].index = i;//确定下标号，虽然这个不用排序，但是可作为固定标记。
                this.partitionArray[i].restSpace = this.partitionArray[i].size;
                this.partitionArray[i].group = this.drawPartition(this.partitionArray[i]);
                this.layer.append(this.partitionArray[i].group);
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

            const arrow_return = this.drawArrow2(this.partitionArray.length);
            this.arrowArray.push(arrow_return);
            this.layer.append(arrow_return);
            this.isSubmit1 = true;



        },
        drawArrow2(n){  
            var len=n*300
            console.log(len)
            var  d= 'md0,d0l d50,d0l d0,d100l d-'+len+',d0l d0,d-100l d50,d0l d-35,d-20m d35,d20l d-35,d20m d35,d20'//l d-35,d-20m d35,d20l d-35,d20m d35,d20
            const arrow = new Path();
            arrow.attr({
              name:"arrow",
              d: d,
              scale:0.5,
              strokeColor: 'black',
              lineWidth: 5,
              pos: [this.next_partition_pos[0]-50,this.next_partition_pos[1]+20],
            });
            return arrow;
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
        },
        findPartitionPos(i) {
            var partitionPos = null;
            var num = 0;
            for (var j = this.last_index_nf; j < this.partitionArray.length; j++){
                num++;
                if(this.partitionArray[j].restSpace>=this.processArray[i].pcb_need_size){
                    partitionPos=j;
                    this.last_index_nf=j;
                    return partitionPos;
                }
                if(num>this.partitionArray.length){
                break;
                } 
                if(j==this.partitionArray.length-1){
                j=-1;
                }
            }
            return partitionPos;       
        },
        allocation(i,partitionPos) {

            if (partitionPos == null) {
                return 0; //无合适分区
            }else if (this.partitionArray[partitionPos].restSpace > this.processArray[i].pcb_need_size) {
                //分割

                this.partitionArray[partitionPos].pid.push(i);
                this.partitionArray[partitionPos].restSpace -= this.processArray[i].pcb_need_size;
                let allocInfo = {
                    t_partition: partitionPos,
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
                    t_partition:  partitionPos,
                    t_pcb: this.processArray[i].pcb_name,
                    t_occupied: this.processArray[i].pcb_need_size,
                     t_rest: this.partitionArray[partitionPos].restSpace,
                     t_first:'no'
                    
                }
                this.allocTables.push(allocInfo);
                return 1;
            }   
        },
        changeFillColor(color,patitionPos) {

       if (color == this.GLOBAL.COLOR_EMPTY) {
            this.partitionArray[patitionPos].group.children[0].animate([
              {fillColor: this.GLOBAL.COLOR_EMPTY},
              {fillColor: this.GLOBAL.COLOR_EMPTY},
            ], {
              delay:this.timeNow,
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
              delay:this.timeNow,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });

             this.partitionArray[patitionPos].group.children[1].animate([
            {text:""},{text:"0K"}
              
            ], {
              delay:this.timeNow,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
             });

            }
          console.log('改完色了')
        },
        findPreLink(k) { //找上一个空闲分区
        for(var j=k-1;j>=0;j--){
            if(this.partitionArray[j].restSpace!=0){
              return j;
            }
          }
          return null;
        },
        findNextLink(k) {
            //找下一个空闲分区
             for(var j=k+1;j<this.partitionArray.length;j++){
            if(this.partitionArray[j].restSpace!=0){
              return j;
            }
          }
          return null;
        },
        findPartitionPosType(k) {//判断分区所处位置
            var flag = true;//默认是头部空闲分区
             for(var j=0;j<k;j++){
                if(this.partitionArray[j].restSpace!=0){
                flag=false;
                }
            }
            if(flag) return 0;
            flag=true;//默认是尾部
            for(var j=this.partitionArray.length-1;j>k;j--){
                if(this.partitionArray[j].restSpace!=0){
                flag=false;
                }
            }
            if(flag) return 2;
            return 1;

            
        },
        //区别于首次适应的画法
        changeArrow(k) {
            //分区状态由空变满，改变分区的前后箭头
        //判断K的位置：0、头部 2、尾部 1、中部

            var type = this.findPartitionPosType(k);
            switch (type) {
            case 0://头部
                this.arrowArray[k].animate([
                  {opacity:1},
                  {opacity:0},
              ], {
                  delay:this.timeNow+500,
                  duration: 0,
                  iterations: 1,
                  fill: 'forwards',
              });
              this.timeNow+=500;
              var preK=this.findPreLink(this.partitionArray.length);//重绘preK,连到下一个
              var nextK=this.findNextLink(0);
              console.log('preK='+preK);
              console.log('nextK='+nextK);
              if(nextK == preK){//只有一个
                this.arrowArray[preK].animate([
                    {opacity:1},
                    {opacity:0},
                ], {
                    delay:this.timeNow+500,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                });
              this.timeNow+=500;
              }else {//重绘
                this.arrowArray[preK].animate([
                    {d: ''},
                    {d: this.drawNewArrow2(preK,nextK)},
                  ], {
                    delay:this.timeNow,
                    duration: 0,
                    iterations: 1,
                    fill: 'forwards',
                });
              }
                    break;
            case 2:
            this.arrowArray[k].animate([
                {opacity:1},
                {opacity:0},
            ], {
                delay:this.timeNow+500,
                duration: 0,
                iterations: 1,
                fill: 'forwards',
            });
            this.timeNow+=500;
            var preK=this.findPreLink(k);//重绘preK,连到下一个
            var nextK=this.findNextLink(-1);
            console.log('preK='+preK+'   nextK='+nextK);
            
            if(nextK == preK){//只有一个
            this.arrowArray[preK].animate([
                {opacity:1},
                {opacity:0},
            ], {
                delay:this.timeNow+500,
                duration: 0,
                iterations: 1,
                fill: 'forwards',
            });
            this.timeNow+=500;
            }else {//重绘
            this.arrowArray[preK].animate([
                {d: ''},
                {d: this.drawNewArrow2(preK,nextK)},
                ], {
                delay:this.timeNow,
                duration: 0,
                iterations: 1,
                fill: 'forwards',
            });
            }
                    break;
                case 1:
                    this.arrowArray[k].animate([//将箭头透明度设置为0，达到隐形的效果
                        { opacity: 1 },
                        { opacity: 0 },
                    ], {
                        delay: this.timeNow + 500,
                        duration: 0,
                        iterations: 1,
                        fill: 'forwards',
                    });
                    this.timeNow += 500;
                    //前面的箭头如果后面没有空区就取消，如果还有就连上
                    //判断前面哪个箭头连的哪个分区
                    var preK = this.findPreLink(k);
                    var nextK = this.findNextLink(k);
                    console.log('preK=' + preK + '   nextK=' + nextK);
                    this.arrowArray[preK].animate([
                        { d: '' },
                        { d: this.drawNewArrow(preK, nextK) },
                    ], {
                        delay: this.timeNow,
                        duration: 0,
                        iterations: 1,
                        fill: 'forwards',
                    });

                    break;



            }
            
        },
        drawNewArrow(m,n){//返回箭头拉长后的路径
          var len=(n-m-1)*300;
          var  d= 'md0,d0l d50,d0l d0,d-100l d'+len+',d0l d0,d100l d50,d0l d-35,d-20m d35,d20l d-35,d20m d35,d20'
          return d;
        },
        drawNewArrow2(m,n){//返回箭头拉长后的路径
          var len=(m-n+1)*300;
          console.log('len='+len)
          var  d= 'md0,d0l d50,d0l d0,d100l d-'+len+',d0l d0,d-100l d50,d0l d-35,d20m d35,d-20l d-35,d-20'
          return d;
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
        changePartition(nextPos,i,j) {
             const text=this.partitionArray[j].restSpace.toString();
          this.partitionArray[j].group.children[1].animate([//修改分区剩余空闲区域
            {text: ""},
            {text:text+"K"},
            ], {
              delay:this.timeNow,
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
              delay:this.timeNow,
              duration: 500,
              iterations: 1,
              fill: 'forwards',
            });
          this.timeNow+=500;
          this.layer.timeline.setTimeout(() => {
            newPartition.group.remove()
          },this.timeNow+1000);
          this.timeNow+=1000;
          console.log('加完分区了')
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
        async onSubmit2() {
            this.processArray = this.process.process_input.concat([]);
            var nextPos = 60;
            for (var i = 0; i < this.processArray.length; i++) {

                //计算空闲分区的位置
                const patitionPos = this.findPartitionPos(i);

                const resultType = this.allocation(i, patitionPos);

                //生成进程
                const pcb = this.drawProcess(this.processArray[i]);
                this.layer.append(pcb);
              
                pcb.animate([
                    { x: nextPos, y: 500 },
                    { x: nextPos, y: this.next_partition_pos[1] + 50 },
                ], {
                    delay: this.timeNow,
                    duration: 1000,
                    iterations: 1,
                    fill: 'forwards',
                });
                this.timeNow += 1000;
                var num = 0;
                for (var j = this.last_index; j < this.partitionArray.length;j++){
                    num++;
                    if (j == patitionPos) {
                        pcb.animate([
                    {x: nextPos,y: this.next_partition_pos[1]+50},
                    {x: nextPos,y: this.next_partition_pos[1]},
                    ], {
                        delay:this.timeNow+2000,
                        duration: 1000,
                        iterations: 1,
                        fill: 'forwards',
                    });

                    this.timeNow += 3000;

                    //消失
                    this.layer.timeline.setTimeout(() => {
                      pcb.remove();
                    },this.timeNow +500);
                        this.timeNow += 500;
                     if (resultType == 1) {
                        //空闲分区被占满
                        //变红
                        this.changeFillColor(this.GLOBAL.COLOR_FULL, j);
                        //改变箭头颜色
                        this.changeArrow(j);
                    } else {
                        //没占满
                        //去红，留白
                        this.changePartition(nextPos,i,j);
                        this.changeFillColor(this.GLOBAL.COLOR_EMPTY,j);

                    }
                    this.last_index = j;
                    break;
                    }
                    else  //还没到正确位置
                    {
                        if(num>this.partitionArray.length ){
                            break;
                        }

                        if (j == this.partitionArray.length - 1) {
                            j = -1;


                            pcb.animate([
                                { x: nextPos, y: this.next_partition_pos[1] + 50 },
                                { x: 60, y: this.next_partition_pos[1] + 50 },
                            ], {
                                delay: this.timeNow + 2000,
                                duration: 1000,
                                iterations: 1,
                                fill: 'forwards',
                            });
                            this.timeNow += 3000;
                            nextPos = 60;
                        } else
                        {
                            nextPos += 150;
                            if (this.partitionArray[j].restSpace==0) {  //给人一种被完全占用的进程块不必比较的感觉
                            pcb.animate([
                            {x: nextPos-150,y: this.next_partition_pos[1]+50},
                            {x: nextPos,y: this.next_partition_pos[1]+50},
                            ], {
                                delay:this.timeNow,
                                duration: 1000,
                                //duration:0,
                                iterations: 1,
                                fill: 'forwards',
                            });
                            this.timeNow+=1000;
                            }else
                            {
                            pcb.animate([
                            {x: nextPos-150,y: this.next_partition_pos[1]+50},
                            {x: nextPos,y: this.next_partition_pos[1]+50},
                            ], {
                                delay:this.timeNow+2000,
                                duration: 1000,
                                //duration:0,
                                iterations: 1,
                                fill: 'forwards',
                            });
                            this.timeNow+=3000;
                            }
                        }
                    }
                }

                 //直接消失掉
                if (resultType == 0) {
                    this.layer.timeline.setTimeout(() => {
                    pcb.remove();
                },this.timeNow+500);
                 this.timeNow+=500;
                }


            }
        }




        

    },
   
    mounted() {
        this.init();
    }
}
</script>