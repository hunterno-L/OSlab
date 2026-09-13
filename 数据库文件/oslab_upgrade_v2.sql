-- =====================================================================
-- OSLab 上线前修复 · 数据库增量变更脚本
-- 适用：已有数据库实例（oslab_db.sql 已内置以下变更，新建库无需重复执行）
-- 执行方式：mysql -uroot -p oslab_db < oslab_upgrade_v2.sql
-- =====================================================================

-- 1) 用户表 userAccount 补唯一索引（防止并发/多实例重复注册账号）
--    注意：若历史数据存在重复账号，先清理重复行再执行本语句。
--    排查重复：SELECT userAccount, COUNT(*) FROM user GROUP BY userAccount HAVING COUNT(*) > 1;
ALTER TABLE `user`
    ADD UNIQUE KEY `uk_userAccount` (`userAccount`);

-- 2) 说明：判题提交限流、登录失败锁定、注册限流为内存实现（单实例有效），
--    无需数据库变更；横向扩容为多实例时需迁移至 Redis 分布式限流。
