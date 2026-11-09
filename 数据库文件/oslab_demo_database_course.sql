SET NAMES utf8mb4;
-- 第二门小课程：数据库系统（用于跨课程检索/判分/记录验证，内容按主流 MySQL 教材整理）
INSERT INTO course_catalog (id,courseCode,title,description,status,isDelete)
VALUES (980000000000000001,'course.database','数据库系统','索引、事务与 SQL 基础的小型课程包，用于验证跨课程能力','PUBLISHED',0)
ON DUPLICATE KEY UPDATE title=VALUES(title),status='PUBLISHED',description=VALUES(description);

INSERT INTO course_section (id,sectionCode,courseCode,parentSectionCode,title,textbookNumber,sectionType,sortOrder,isDelete)
VALUES (980000000000000002,'section.db.basics','course.database',NULL,'数据库基础','1','CHAPTER',1,0)
ON DUPLICATE KEY UPDATE title=VALUES(title);

INSERT INTO learning_unit (id,unitCode,courseCode,sectionCode,title,sortOrder,estimatedMinutes,revision,reviewStatus,draftJson,publishedJson,userId,contributor,isDelete)
VALUES (980000000000000003,'unit.db.index','course.database','section.db.basics','索引与查询优化',1,20,1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0)
ON DUPLICATE KEY UPDATE title=VALUES(title),reviewStatus='PUBLISHED';

INSERT INTO learning_unit (id,unitCode,courseCode,sectionCode,title,sortOrder,estimatedMinutes,revision,reviewStatus,draftJson,publishedJson,userId,contributor,isDelete)
VALUES (980000000000000004,'unit.db.transaction','course.database','section.db.basics','事务与隔离级别',2,20,1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0)
ON DUPLICATE KEY UPDATE title=VALUES(title),reviewStatus='PUBLISHED';

INSERT INTO learning_question (id,title,content,type,options,answer,analysis,misconception,chapter,difficulty,reviewStatus,userId,isDelete,questionCode,revision,sectionCode,resourceScope)
VALUES
 (980000000000000101,'InnoDB 聚簇索引的叶子节点存放什么？','InnoDB 中聚簇索引（主键索引）的叶子节点存放的是？','SINGLE',
  JSON_ARRAY(JSON_OBJECT('key','A','text','主键值'),JSON_OBJECT('key','B','text','整行数据'),JSON_OBJECT('key','C','text','指向行的物理地址'),JSON_OBJECT('key','D','text','仅索引列')),
  JSON_ARRAY('B'),'InnoDB 聚簇索引的叶子节点就是数据页本身，存放整行记录；二级索引叶子节点才存放主键值，需要回表。','误以为索引叶子节点只存指针，按 MyISAM 的非聚簇结构理解 InnoDB。','数据库系统',2,'PUBLISHED',1,0,'q.db.clustered',1,'section.db.basics','SHARED'),
 (980000000000000102,'什么情况下会发生回表？','使用二级索引查询非索引列时，为什么需要回表？','SINGLE',
  JSON_ARRAY(JSON_OBJECT('key','A','text','二级索引叶子节点只有主键值，需要再查聚簇索引'),JSON_OBJECT('key','B','text','因为缓冲池未命中'),JSON_OBJECT('key','C','text','因为事务隔离级别太低'),JSON_OBJECT('key','D','text','因为 SQL 没有加索引提示')),
  JSON_ARRAY('A'),'二级索引叶子节点存主键值，查到主键后还要按主键再走一次聚簇索引，这就是回表。覆盖索引可以避免回表。','把回表当成缓存未命中，忽略了二级索引与聚簇索引的两棵树结构。','数据库系统',2,'PUBLISHED',1,0,'q.db.backtable',1,'section.db.basics','SHARED'),
 (980000000000000103,'覆盖索引能减少什么开销？','把查询需要的列都放进索引，主要减少哪类开销？','SINGLE',
  JSON_ARRAY(JSON_OBJECT('key','A','text','磁盘扫描整表'),JSON_OBJECT('key','B','text','回表带来的随机 IO'),JSON_OBJECT('key','C','text','事务提交的日志写入'),JSON_OBJECT('key','D','text','连接建立的握手')),
  JSON_ARRAY('B'),'覆盖索引让查询列都在索引里，不需要回到聚簇索引取行，减少随机 IO。','把覆盖索引理解成缓存命中，实际是避免第二次索引查找。','数据库系统',2,'PUBLISHED',1,0,'q.db.covering',1,'section.db.basics','SHARED'),
 (980000000000000104,'事务的 ACID 中，一致性由谁保证？','ACID 里的 C（一致性）主要依靠什么保证？','SINGLE',
  JSON_ARRAY(JSON_OBJECT('key','A','text','只靠 undo log'),JSON_OBJECT('key','B','text','应用层约束与数据库约束共同保证'),JSON_OBJECT('key','C','text','只靠隔离级别'),JSON_OBJECT('key','D','text','只靠 redo log')),
  JSON_ARRAY('B'),'原子性靠 undo，持久性靠 redo，隔离性靠锁与 MVCC；一致性需要约束、触发器和应用逻辑共同保证。','把一致性等同于隔离性，认为调高隔离级别就自动一致。','数据库系统',2,'PUBLISHED',1,0,'q.db.acid',1,'section.db.basics','SHARED'),
 (980000000000000105,'读未提交会带来什么问题？','隔离级别 READ UNCOMMITTED 最典型的问题是？','SINGLE',
  JSON_ARRAY(JSON_OBJECT('key','A','text','脏读'),JSON_OBJECT('key','B','text','幻读'),JSON_OBJECT('key','C','text','死锁'),JSON_OBJECT('key','D','text','写偏斜')),
  JSON_ARRAY('A'),'读未提交能读到别的事务尚未提交的数据，事务回滚后就读到了不存在的数据，即脏读。','把脏读和不可重复读混为一谈，前者读到未提交数据。','数据库系统',2,'PUBLISHED',1,0,'q.db.dirtyread',1,'section.db.basics','SHARED'),
 (980000000000000106,'可重复读能避免幻读吗？','MySQL InnoDB 的 REPEATABLE READ 下，幻读是否完全避免？','SINGLE',
  JSON_ARRAY(JSON_OBJECT('key','A','text','完全避免'),JSON_OBJECT('key','B','text','快照读避免，当前读仍可能出现'),JSON_OBJECT('key','C','text','完全无法避免'),JSON_OBJECT('key','D','text','只在只读事务中避免')),
  JSON_ARRAY('B'),'InnoDB 在 RR 下用 MVCC 让快照读看不到新插入的行；但当前读（加锁读）仍可能遇到幻读，需要间隙锁。','认为 RR 等于串行化，忽略快照读与当前读的区别。','数据库系统',2,'PUBLISHED',1,0,'q.db.phantom',1,'section.db.basics','SHARED')
ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),options=VALUES(options),answer=VALUES(answer),analysis=VALUES(analysis),misconception=VALUES(misconception),reviewStatus='PUBLISHED';

INSERT INTO learning_question_node (id,questionId,nodeName,weight,nodeCode)
VALUES
 (980000000000000201,980000000000000101,'数据库索引',1,'db.index'),
 (980000000000000202,980000000000000102,'数据库索引',1,'db.index'),
 (980000000000000203,980000000000000103,'数据库索引',1,'db.index'),
 (980000000000000204,980000000000000104,'事务',1,'db.transaction'),
 (980000000000000205,980000000000000105,'事务',1,'db.transaction'),
 (980000000000000206,980000000000000106,'事务',1,'db.transaction')
ON DUPLICATE KEY UPDATE nodeName=VALUES(nodeName),nodeCode=VALUES(nodeCode);

INSERT INTO learning_content_chunk (id,chunkCode,courseCode,sectionCode,title,chunkType,contentMd,revision,reviewStatus,draftJson,publishedJson,userId,contributor,isDelete)
VALUES
 (980000000000000301,'chunk.db.index.bplus','course.database','section.db.basics','B+ 树索引结构','TEXT','InnoDB 使用 B+ 树组织索引。非叶子节点只存键值用于导航，叶子节点存放数据并用链表相连，因此范围查询只需在叶子层顺序扫描。B+ 树高度通常为 3 到 4 层，一次等值查询只需少量页读取。',1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0),
 (980000000000000302,'chunk.db.index.clustered','course.database','section.db.basics','聚簇索引与二级索引','TEXT','聚簇索引按主键组织数据，叶子节点就是数据页，存放整行记录。二级索引的叶子节点存放索引列和主键值，查询非索引列时需要按主键回到聚簇索引再查一次，这个过程叫回表。',1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0),
 (980000000000000303,'chunk.db.index.covering','course.database','section.db.basics','覆盖索引与索引下推','TEXT','如果查询需要的列全部包含在二级索引中，就无需回表，这种索引称为覆盖索引，能显著减少随机 IO。索引下推把 WHERE 条件下推到存储引擎层过滤，减少回传给 Server 层的行数。',1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0),
 (980000000000000304,'chunk.db.tx.acid','course.database','section.db.basics','事务与 ACID','TEXT','事务具有原子性、一致性、隔离性和持久性。原子性由 undo log 保证，持久性由 redo log 保证，隔离性由锁和 MVCC 保证。一致性是目标，需要约束、触发器和应用逻辑共同维护，单靠数据库某一机制无法保证。',1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0),
 (980000000000000305,'chunk.db.tx.levels','course.database','section.db.basics','隔离级别与并发异常','TEXT','SQL 标准定义四个隔离级别：读未提交、读已提交、可重复读、串行化。读未提交会产生脏读；读已提交避免脏读但可能不可重复读；可重复读用 MVCC 提供一致性快照；串行化用锁避免幻读。',1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0),
 (980000000000000306,'chunk.db.tx.mvcc','course.database','section.db.basics','MVCC 与幻读','TEXT','InnoDB 的可重复读通过 MVCC 让快照读看到事务开始时的版本，因此普通 SELECT 不会出现幻读。但加锁的当前读仍可能读到新插入的行，需要用间隙锁或 Next-Key Lock 阻止其他事务在区间内插入。',1,'PUBLISHED',JSON_OBJECT(),JSON_OBJECT(),1,'system',0)
ON DUPLICATE KEY UPDATE title=VALUES(title),contentMd=VALUES(contentMd),reviewStatus='PUBLISHED';
