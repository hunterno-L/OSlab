SET NAMES utf8mb4;

UPDATE learning_unit
SET publishedJson = JSON_OBJECT(
  'unitCode', 'unit.db.index',
  'courseCode', 'course.database',
  'sectionCode', 'section.db.basics',
  'title', '索引与查询优化',
  'revision', 1,
  'sortOrder', 1,
  'estimatedMinutes', 20,
  'objectives', JSON_ARRAY('理解 B+ 树索引结构', '说明聚簇索引与二级索引的差异', '解释回表与覆盖索引'),
  'nodeMappings', JSON_ARRAY(JSON_OBJECT('role', 'TEACHES', 'nodeCode', 'db.index')),
  'activities', JSON_ARRAY(
    JSON_OBJECT('activityCode', 'act.db.index.read.1', 'activityType', 'READING', 'title', 'B+ 树索引结构',
                'sortOrder', 1, 'required', true, 'completionRule', 'CONFIRM',
                'reference', JSON_OBJECT('kind', 'CHUNK', 'code', 'chunk.db.index.bplus')),
    JSON_OBJECT('activityCode', 'act.db.index.read.2', 'activityType', 'READING', 'title', '聚簇索引与二级索引',
                'sortOrder', 2, 'required', true, 'completionRule', 'CONFIRM',
                'reference', JSON_OBJECT('kind', 'CHUNK', 'code', 'chunk.db.index.clustered')),
    JSON_OBJECT('activityCode', 'act.db.index.read.3', 'activityType', 'READING', 'title', '覆盖索引',
                'sortOrder', 3, 'required', true, 'completionRule', 'CONFIRM',
                'reference', JSON_OBJECT('kind', 'CHUNK', 'code', 'chunk.db.index.covering'))
  ),
  'questionMappings', JSON_ARRAY(
    JSON_OBJECT('usage', 'PRACTICE', 'questionCode', 'q.db.clustered'),
    JSON_OBJECT('usage', 'PRACTICE', 'questionCode', 'q.db.backtable'),
    JSON_OBJECT('usage', 'PRACTICE', 'questionCode', 'q.db.covering')
  )
)
WHERE unitCode = 'unit.db.index';

UPDATE learning_unit
SET publishedJson = JSON_OBJECT(
  'unitCode', 'unit.db.transaction',
  'courseCode', 'course.database',
  'sectionCode', 'section.db.basics',
  'title', '事务与隔离级别',
  'revision', 1,
  'sortOrder', 2,
  'estimatedMinutes', 20,
  'objectives', JSON_ARRAY('理解 ACID 各自靠什么保证', '区分四个隔离级别', '解释 MVCC 与幻读'),
  'nodeMappings', JSON_ARRAY(JSON_OBJECT('role', 'TEACHES', 'nodeCode', 'db.transaction')),
  'activities', JSON_ARRAY(
    JSON_OBJECT('activityCode', 'act.db.tx.read.1', 'activityType', 'READING', 'title', '事务与 ACID',
                'sortOrder', 1, 'required', true, 'completionRule', 'CONFIRM',
                'reference', JSON_OBJECT('kind', 'CHUNK', 'code', 'chunk.db.tx.acid')),
    JSON_OBJECT('activityCode', 'act.db.tx.read.2', 'activityType', 'READING', 'title', '隔离级别与并发异常',
                'sortOrder', 2, 'required', true, 'completionRule', 'CONFIRM',
                'reference', JSON_OBJECT('kind', 'CHUNK', 'code', 'chunk.db.tx.levels')),
    JSON_OBJECT('activityCode', 'act.db.tx.read.3', 'activityType', 'READING', 'title', 'MVCC 与幻读',
                'sortOrder', 3, 'required', true, 'completionRule', 'CONFIRM',
                'reference', JSON_OBJECT('kind', 'CHUNK', 'code', 'chunk.db.tx.mvcc'))
  ),
  'questionMappings', JSON_ARRAY(
    JSON_OBJECT('usage', 'PRACTICE', 'questionCode', 'q.db.acid'),
    JSON_OBJECT('usage', 'PRACTICE', 'questionCode', 'q.db.dirtyread'),
    JSON_OBJECT('usage', 'PRACTICE', 'questionCode', 'q.db.phantom')
  )
)
WHERE unitCode = 'unit.db.transaction';
