-- 语义检索索引：教材块向量（平台级，模型与维度固定）
CREATE TABLE IF NOT EXISTS `ai_chunk_embedding` (
  `chunkCode` VARCHAR(128) NOT NULL,
  `courseCode` VARCHAR(128) NOT NULL,
  `model` VARCHAR(128) NOT NULL,
  `dim` INT NOT NULL,
  `vectorJson` MEDIUMTEXT NOT NULL,
  `textHash` VARCHAR(64) NOT NULL,
  `updateTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`chunkCode`),
  KEY `idx_ai_emb_course` (`courseCode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
