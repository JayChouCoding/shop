-- -*- coding: utf-8 -*-
-- =========================================
-- SuddenFix database bootstrap script
-- Notes:
-- 1. product and agent services use single databases
-- 2. user, order and pay services use 2 databases with 16 shards
-- 3. shipping tables share the same shard key with orders
-- 4. local message tables are created in order and pay shard databases
-- =========================================

SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- 1. product service database
CREATE DATABASE IF NOT EXISTS suddenfix_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. agent service database
CREATE DATABASE IF NOT EXISTS suddenfix_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. user service shard databases
CREATE DATABASE IF NOT EXISTS suddenfix_user_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS suddenfix_user_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 4. order service shard databases
CREATE DATABASE IF NOT EXISTS suddenfix_order_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS suddenfix_order_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 5. pay service shard databases
CREATE DATABASE IF NOT EXISTS suddenfix_pay_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS suddenfix_pay_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =========================================
-- Coupon tables
-- Managed by order service for now.
-- These tables must exist in both order shard databases, otherwise
-- ShardingSphere metadata binding will throw TableNotFoundException.
-- =========================================
USE suddenfix_order_0;

CREATE TABLE IF NOT EXISTS `coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'coupon activity id',
  `name` VARCHAR(64) NOT NULL COMMENT 'coupon name',
  `amount` DECIMAL(10,2) NOT NULL DEFAULT '0.00' COMMENT 'coupon amount',
  `min_point` DECIMAL(10,2) NOT NULL DEFAULT '0.00' COMMENT 'minimum spend',
  `total_stock` INT NOT NULL COMMENT 'total stock',
  `segment_count` INT NOT NULL DEFAULT '10' COMMENT 'redis segment count',
  `status` TINYINT NOT NULL DEFAULT '0' COMMENT '0 not started, 1 active, 2 finished',
  `start_time` DATETIME NOT NULL COMMENT 'activity start time',
  `end_time` DATETIME NOT NULL COMMENT 'activity end time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='coupon activity table';

CREATE TABLE IF NOT EXISTS `coupon_claim_record` (
  `id` BIGINT NOT NULL COMMENT 'claim record id',
  `coupon_id` BIGINT NOT NULL COMMENT 'coupon id',
  `user_id` BIGINT NOT NULL COMMENT 'user id',
  `segment_index` INT NOT NULL COMMENT 'segment index',
  `coupon_token` VARCHAR(128) NOT NULL COMMENT 'unique coupon token',
  `status` TINYINT NOT NULL DEFAULT '0' COMMENT '0 unused, 1 used, 2 expired, 3 rolled back',
  `order_id` BIGINT DEFAULT NULL COMMENT 'related order id',
  `used_time` DATETIME DEFAULT NULL COMMENT 'used time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_user` (`coupon_id`, `user_id`),
  UNIQUE KEY `uk_coupon_token` (`coupon_token`),
  KEY `idx_claim_user` (`user_id`),
  KEY `idx_claim_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='coupon claim record table';

USE suddenfix_order_1;

CREATE TABLE IF NOT EXISTS `coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'coupon activity id',
  `name` VARCHAR(64) NOT NULL COMMENT 'coupon name',
  `amount` DECIMAL(10,2) NOT NULL DEFAULT '0.00' COMMENT 'coupon amount',
  `min_point` DECIMAL(10,2) NOT NULL DEFAULT '0.00' COMMENT 'minimum spend',
  `total_stock` INT NOT NULL COMMENT 'total stock',
  `segment_count` INT NOT NULL DEFAULT '10' COMMENT 'redis segment count',
  `status` TINYINT NOT NULL DEFAULT '0' COMMENT '0 not started, 1 active, 2 finished',
  `start_time` DATETIME NOT NULL COMMENT 'activity start time',
  `end_time` DATETIME NOT NULL COMMENT 'activity end time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT '0' COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='coupon activity table';

CREATE TABLE IF NOT EXISTS `coupon_claim_record` (
  `id` BIGINT NOT NULL COMMENT 'claim record id',
  `coupon_id` BIGINT NOT NULL COMMENT 'coupon id',
  `user_id` BIGINT NOT NULL COMMENT 'user id',
  `segment_index` INT NOT NULL COMMENT 'segment index',
  `coupon_token` VARCHAR(128) NOT NULL COMMENT 'unique coupon token',
  `status` TINYINT NOT NULL DEFAULT '0' COMMENT '0 unused, 1 used, 2 expired, 3 rolled back',
  `order_id` BIGINT DEFAULT NULL COMMENT 'related order id',
  `used_time` DATETIME DEFAULT NULL COMMENT 'used time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_user` (`coupon_id`, `user_id`),
  UNIQUE KEY `uk_coupon_token` (`coupon_token`),
  KEY `idx_claim_user` (`user_id`),
  KEY `idx_claim_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='coupon claim record table';

-- =========================================
-- Product service
-- =========================================
USE suddenfix_product;

CREATE TABLE IF NOT EXISTS `t_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'product id',
  `name` VARCHAR(128) NOT NULL COMMENT 'product name',
  `category_id` BIGINT NOT NULL COMMENT 'category id',
  `main_image` VARCHAR(512) NOT NULL COMMENT 'main image url',
  `price` BIGINT NOT NULL COMMENT 'price in cents',
  `stock` INT NOT NULL DEFAULT '0' COMMENT 'current stock',
  `description` TEXT COMMENT 'product description',
  `status` TINYINT NOT NULL DEFAULT '1' COMMENT '0 offline, 1 online',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `start_time` DATETIME DEFAULT NULL COMMENT 'flash sale start time',
  `end_time` DATETIME DEFAULT NULL COMMENT 'flash sale end time',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_category` (`category_id`),
  KEY `idx_product_status` (`status`),
  KEY `idx_product_window` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product s table';

CREATE TABLE IF NOT EXISTS `t_stock_flow` (
  `flow_id` BIGINT NOT NULL COMMENT 'stock flow id',
  `product_id` BIGINT NOT NULL COMMENT 'product id',
  `order_id` BIGINT DEFAULT NULL COMMENT 'related order id',
  `business_type` VARCHAR(32) NOT NULL COMMENT 'ORDER/ROLLBACK/MANUAL/REPAIR',
  `change_type` TINYINT NOT NULL COMMENT '1 pre deduct, 2 confirm deduct, 3 rollback, 4 manual adjust',
  `change_amount` INT NOT NULL COMMENT 'stock delta, positive adds stock and negative deducts stock',
  `before_stock` INT NOT NULL COMMENT 'stock before change',
  `after_stock` INT NOT NULL COMMENT 'stock after change',
  `trace_id` VARCHAR(64) NOT NULL COMMENT 'idempotent trace id',
  `operator` VARCHAR(64) DEFAULT NULL COMMENT 'operator',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'remark',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_product_trace` (`product_id`, `trace_id`, `change_type`),
  KEY `idx_stock_flow_order` (`order_id`),
  KEY `idx_stock_flow_product_time` (`product_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product stock flow table';

CREATE TABLE IF NOT EXISTS `t_local_msg` (
  `msg_id` BIGINT NOT NULL COMMENT 'message id',
  `business_id` BIGINT NOT NULL COMMENT 'business key',
  `topic` VARCHAR(128) NOT NULL COMMENT 'routing topic',
  `payload` TEXT NOT NULL COMMENT 'message payload',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending,1-sent,2-dead',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'retry times',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT 'next retry time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`msg_id`),
  UNIQUE KEY `uk_product_msg_business` (`business_id`),
  KEY `idx_product_msg_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product local message table';

-- =========================================
-- Agent service
-- =========================================
USE suddenfix_agent;

CREATE TABLE IF NOT EXISTS `t_agent_task` (
  `id` BIGINT NOT NULL COMMENT 'task id',
  `user_id` BIGINT NOT NULL COMMENT 'user id',
  `prompt` TEXT NOT NULL COMMENT 'user prompt',
  `ai_result` TEXT COMMENT 'ai result',
  `status` TINYINT NOT NULL DEFAULT '0' COMMENT '0 processing, 1 done, 2 failed',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agent task table';

-- =========================================
-- User service shards
-- =========================================
DROP PROCEDURE IF EXISTS init_sharding_user;
DELIMITER $$
CREATE PROCEDURE init_sharding_user()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE db_name VARCHAR(50);

    WHILE i < 16 DO
        SET db_name = CONCAT('suddenfix_user_', i % 2);

        SET @sql1 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_user_', i, ' (
            `id` BIGINT NOT NULL COMMENT ''user id'',
            `username` VARCHAR(64) DEFAULT NULL COMMENT ''username'',
            `password` VARCHAR(128) DEFAULT NULL COMMENT ''bcrypt password'',
            `email` VARCHAR(64) DEFAULT NULL COMMENT ''email'',
            `phone` VARCHAR(32) DEFAULT NULL COMMENT ''phone'',
            `nickname` VARCHAR(64) DEFAULT NULL COMMENT ''nickname'',
            `avatar` VARCHAR(512) DEFAULT NULL COMMENT ''avatar'',
            `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''1 enabled, 0 disabled'',
            `role` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 customer, 1 merchant'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`id`),
            KEY `idx_user_status` (`status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''user table'';');
        PREPARE stmt1 FROM @sql1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

        SET @sql2 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_user_mapping_username_', i, ' (
            `username` VARCHAR(64) NOT NULL COMMENT ''username'',
            `id` BIGINT NOT NULL COMMENT ''user id'',
            PRIMARY KEY (`username`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''username mapping table'';');
        PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

        SET @sql3 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_user_mapping_phone_', i, ' (
            `phone` VARCHAR(32) NOT NULL COMMENT ''phone'',
            `id` BIGINT NOT NULL COMMENT ''user id'',
            PRIMARY KEY (`phone`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''phone mapping table'';');
        PREPARE stmt3 FROM @sql3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

        SET @sql4 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_user_mapping_email_', i, ' (
            `email` VARCHAR(64) NOT NULL COMMENT ''email'',
            `id` BIGINT NOT NULL COMMENT ''user id'',
            PRIMARY KEY (`email`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''email mapping table'';');
        PREPARE stmt4 FROM @sql4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

        SET @sql5 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_user_address_', i, ' (
            `id` BIGINT NOT NULL COMMENT ''address id'',
            `user_id` BIGINT NOT NULL COMMENT ''user id'',
            `consignee` VARCHAR(64) DEFAULT NULL COMMENT ''consignee'',
            `phone` VARCHAR(32) DEFAULT NULL COMMENT ''contact phone'',
            `province` VARCHAR(32) DEFAULT NULL COMMENT ''province'',
            `city` VARCHAR(32) DEFAULT NULL COMMENT ''city'',
            `district` VARCHAR(32) DEFAULT NULL COMMENT ''district'',
            `detail_address` VARCHAR(256) DEFAULT NULL COMMENT ''detail address'',
            `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT ''default address flag'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`id`),
            KEY `idx_user_address_user` (`user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''user address table'';');
        PREPARE stmt5 FROM @sql5; EXECUTE stmt5; DEALLOCATE PREPARE stmt5;

        SET @sql6 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_user_auth_', i, ' (
            `user_id` BIGINT NOT NULL COMMENT ''user id'',
            `real_name` VARCHAR(64) DEFAULT NULL COMMENT ''real name'',
            `id_card` VARCHAR(64) DEFAULT NULL COMMENT ''id card'',
            `auth_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 unverified, 1 reviewing, 2 passed, 3 rejected'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''user auth table'';');
        PREPARE stmt6 FROM @sql6; EXECUTE stmt6; DEALLOCATE PREPARE stmt6;

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL init_sharding_user();

-- =========================================
-- Order service shards
-- =========================================
DROP PROCEDURE IF EXISTS init_sharding_order;
DELIMITER $$
CREATE PROCEDURE init_sharding_order()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE db_name VARCHAR(50);

    WHILE i < 16 DO
        SET db_name = CONCAT('suddenfix_order_', i % 2);

        SET @sql1 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_order_', i, ' (
            `order_id` BIGINT NOT NULL COMMENT ''order id'',
            `order_no` VARCHAR(64) DEFAULT NULL COMMENT ''order no'',
            `user_id` BIGINT NOT NULL COMMENT ''user id'',
            `total_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''total amount in cents'',
            `freight_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''freight amount in cents'',
            `discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''discount amount in cents'',
            `pay_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''pay amount in cents'',
            `status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 init, 10 pending payment, 20 paid, 30 shipped, 40 finished, 50 closed'',
            `receiver_name` VARCHAR(64) DEFAULT NULL COMMENT ''receiver name'',
            `receiver_phone` VARCHAR(32) DEFAULT NULL COMMENT ''receiver phone'',
            `receiver_address` VARCHAR(256) DEFAULT NULL COMMENT ''receiver address'',
            `pay_channel` TINYINT DEFAULT NULL COMMENT ''pay channel'',
            `out_trade_no` VARCHAR(128) DEFAULT NULL COMMENT ''out trade no'',
            `pay_time` DATETIME DEFAULT NULL COMMENT ''pay time'',
            `ship_time` DATETIME DEFAULT NULL COMMENT ''ship time'',
            `finish_time` DATETIME DEFAULT NULL COMMENT ''finish time'',
            `cancel_time` DATETIME DEFAULT NULL COMMENT ''cancel time'',
            `remark` VARCHAR(255) DEFAULT NULL COMMENT ''remark'',
            `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''logical delete flag'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`order_id`),
            UNIQUE KEY `uk_order_no` (`order_no`),
            KEY `idx_order_user_status` (`user_id`, `status`),
            KEY `idx_order_create_time` (`create_time`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''order table'';');
        PREPARE stmt1 FROM @sql1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

        SET @sql2 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_order_item_', i, ' (
            `item_id` BIGINT NOT NULL COMMENT ''order item id'',
            `order_id` BIGINT NOT NULL COMMENT ''order id'',
            `user_id` BIGINT NOT NULL COMMENT ''user id'',
            `product_id` BIGINT NOT NULL COMMENT ''product id'',
            `product_name` VARCHAR(128) DEFAULT NULL COMMENT ''product name'',
            `price` BIGINT NOT NULL DEFAULT 0 COMMENT ''unit price in cents'',
            `quantity` BIGINT NOT NULL DEFAULT 0 COMMENT ''quantity'',
            `total_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''total amount in cents'',
            `discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''discount amount in cents'',
            `real_pay_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''real pay amount in cents'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`item_id`),
            KEY `idx_order_item_order` (`order_id`),
            KEY `idx_order_item_user` (`user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''order item table'';');
        PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

        SET @sql3 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_shipping_', i, ' (
            `shipping_id` BIGINT NOT NULL COMMENT ''shipping id'',
            `order_id` BIGINT NOT NULL COMMENT ''order id'',
            `user_id` BIGINT NOT NULL COMMENT ''user id'',
            `logistics_no` VARCHAR(64) DEFAULT NULL COMMENT ''logistics no'',
            `express_company` VARCHAR(64) DEFAULT NULL COMMENT ''express company'',
            `shipping_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 pending shipment, 1 shipped, 2 in transit, 3 signed, 4 finished, 5 abnormal'',
            `receiver_name` VARCHAR(64) DEFAULT NULL COMMENT ''receiver name'',
            `receiver_phone` VARCHAR(32) DEFAULT NULL COMMENT ''receiver phone'',
            `receiver_address` VARCHAR(256) DEFAULT NULL COMMENT ''receiver address'',
            `ship_time` DATETIME DEFAULT NULL COMMENT ''ship time'',
            `sign_time` DATETIME DEFAULT NULL COMMENT ''sign time'',
            `remark` VARCHAR(255) DEFAULT NULL COMMENT ''remark'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`shipping_id`),
            UNIQUE KEY `uk_shipping_order` (`order_id`),
            UNIQUE KEY `uk_shipping_logistics_no` (`logistics_no`),
            KEY `idx_shipping_user_status` (`user_id`, `shipping_status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''shipping table'';');
        PREPARE stmt3 FROM @sql3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL init_sharding_order();

CREATE TABLE IF NOT EXISTS suddenfix_order_0.t_local_msg_0 (
  `msg_id` BIGINT NOT NULL COMMENT 'message id',
  `business_id` BIGINT NOT NULL COMMENT 'business key',
  `topic` VARCHAR(64) NOT NULL COMMENT 'target topic',
  `payload` TEXT NOT NULL COMMENT 'message payload',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 sent, 2 dead',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  `max_retry_count` INT NOT NULL DEFAULT 16 COMMENT 'max retry count',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT 'next retry time',
  `error_msg` VARCHAR(255) DEFAULT NULL COMMENT 'last error message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`msg_id`),
  UNIQUE KEY `uk_order_msg_business` (`business_id`),
  KEY `idx_order_msg_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order local message table';

CREATE TABLE IF NOT EXISTS suddenfix_order_1.t_local_msg_1 (
  `msg_id` BIGINT NOT NULL COMMENT 'message id',
  `business_id` BIGINT NOT NULL COMMENT 'business key',
  `topic` VARCHAR(64) NOT NULL COMMENT 'target topic',
  `payload` TEXT NOT NULL COMMENT 'message payload',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 sent, 2 dead',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  `max_retry_count` INT NOT NULL DEFAULT 16 COMMENT 'max retry count',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT 'next retry time',
  `error_msg` VARCHAR(255) DEFAULT NULL COMMENT 'last error message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`msg_id`),
  UNIQUE KEY `uk_order_msg_business` (`business_id`),
  KEY `idx_order_msg_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order local message table';

-- =========================================
-- Pay service shards
-- =========================================
DROP PROCEDURE IF EXISTS init_sharding_pay;
DELIMITER $$
CREATE PROCEDURE init_sharding_pay()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE db_name VARCHAR(50);

    WHILE i < 16 DO
        SET db_name = CONCAT('suddenfix_pay_', i % 2);

        SET @sql1 = CONCAT('CREATE TABLE IF NOT EXISTS ', db_name, '.t_pay_record_', i, ' (
            `pay_id` BIGINT NOT NULL COMMENT ''pay id'',
            `order_id` BIGINT NOT NULL COMMENT ''order id'',
            `user_id` BIGINT NOT NULL COMMENT ''user id'',
            `out_trade_no` VARCHAR(128) DEFAULT NULL COMMENT ''out trade no'',
            `channel_trade_no` VARCHAR(128) DEFAULT NULL COMMENT ''channel trade no'',
            `pay_channel` TINYINT DEFAULT NULL COMMENT ''pay channel'',
            `amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''amount in cents'',
            `status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 pending, 1 paid, 2 failed, 3 refunded or closed'',
            `pay_time` DATETIME DEFAULT NULL COMMENT ''pay time'',
            `error_msg` VARCHAR(255) DEFAULT NULL COMMENT ''error message'',
            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`pay_id`),
            UNIQUE KEY `uk_pay_order_id` (`order_id`),
            UNIQUE KEY `uk_pay_out_trade_no` (`out_trade_no`),
            KEY `idx_pay_user_status` (`user_id`, `status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''pay record table'';');
        PREPARE stmt1 FROM @sql1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL init_sharding_pay();

CREATE TABLE IF NOT EXISTS suddenfix_pay_0.t_local_msg_0 (
  `msg_id` BIGINT NOT NULL COMMENT 'message id',
  `business_id` BIGINT NOT NULL COMMENT 'business key',
  `topic` VARCHAR(64) NOT NULL COMMENT 'target topic',
  `payload` TEXT NOT NULL COMMENT 'message payload',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 sent, 2 dead',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  `max_retry_count` INT NOT NULL DEFAULT 16 COMMENT 'max retry count',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT 'next retry time',
  `error_msg` VARCHAR(255) DEFAULT NULL COMMENT 'last error message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`msg_id`),
  UNIQUE KEY `uk_pay_msg_business` (`business_id`),
  KEY `idx_pay_msg_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='pay local message table';

CREATE TABLE IF NOT EXISTS suddenfix_pay_1.t_local_msg_1 (
  `msg_id` BIGINT NOT NULL COMMENT 'message id',
  `business_id` BIGINT NOT NULL COMMENT 'business key',
  `topic` VARCHAR(64) NOT NULL COMMENT 'target topic',
  `payload` TEXT NOT NULL COMMENT 'message payload',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 sent, 2 dead',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  `max_retry_count` INT NOT NULL DEFAULT 16 COMMENT 'max retry count',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT 'next retry time',
  `error_msg` VARCHAR(255) DEFAULT NULL COMMENT 'last error message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`msg_id`),
  UNIQUE KEY `uk_pay_msg_business` (`business_id`),
  KEY `idx_pay_msg_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='pay local message table';
