# 页面置换诊断迁移活动 · 人工核验记录

- 批次：`batch.wd25.memory.diagnostic.002`
- 内容包：`questions/a-memory-diagnostic-002.json`
- 范围：4 道自编客观题，复用已有节点与页面置换计算器，不新增算法、不改种子数据。

## 逐题核验

| 题目 | 题型 | 答案 | 轨迹/依据 | 对应误区 |
| --- | --- | --- | --- | --- |
| `q.self.diag.lru.hit-update` | 单选 | B（4） | LRU 3 页框：5,4,3 装入；命中 5 后顺序 4→3→5；访问 2 换出 4 | `lru.hit-no-update` |
| `q.self.diag.fifo.hit-order` | 单选 | C（6） | FIFO 3 页框：6,1,2 装入；命中 6 不重排；访问 3 换出 6 | `fifo.hit-reorders` |
| `q.self.diag.lru-vs-fifo` | 多选 | A、D | 序列 1,2,3,1,4：FIFO 命中 1 不重排，换出 1；LRU 命中 1 移到最近端，换出 2 | `fifo.lru-confusion` |
| `q.self.diag.free-frame-evictions` | 填空 | 5 | FIFO 4 页框：前 4 次缺页无换出；第 7—11 次缺页换出 5 次；总缺页 9 次 | `fault.vs-eviction`、`free-frame-no-fault` |

## 复核说明

1. 四题均使用与 `PredictCaseCatalog` 一致的计算规则（FIFO 命中不调整顺序；LRU 命中更新最近使用顺序；空页框装入不算换出）。
2. 每题均绑定已有节点，主考查节点与误区一致；没有把同一道题改换题号重复提交。
3. 选项中的提示语用于误区诊断，不作为正确答案线索；正确答案仍需按轨迹计算。
4. 导入后需经真实审核流（DRAFT → SUBMITTED → APPROVED → PUBLISHED）才能被导师选择。
