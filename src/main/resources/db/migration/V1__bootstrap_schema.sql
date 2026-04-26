/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19  Distrib 10.11.16-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: localhost    Database: dbo
-- ------------------------------------------------------
-- Server version	10.11.16-MariaDB-ubu2204

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Sequence structure for `auction_house_file_log_seq`
--

DROP SEQUENCE IF EXISTS `auction_house_file_log_seq`;
CREATE SEQUENCE `auction_house_file_log_seq` start with 1 minvalue 1 maxvalue 9223372036854775806 increment by 50 nocache nocycle ENGINE=InnoDB;
DO SETVAL(`auction_house_file_log_seq`, 1, 0);

--
-- Sequence structure for `auction_house_seq`
--

DROP SEQUENCE IF EXISTS `auction_house_seq`;
CREATE SEQUENCE `auction_house_seq` start with 1 minvalue 1 maxvalue 9223372036854775806 increment by 50 nocache nocycle ENGINE=InnoDB;
DO SETVAL(`auction_house_seq`, 1, 0);

--
-- Sequence structure for `file_reference_seq`
--

DROP SEQUENCE IF EXISTS `file_reference_seq`;
CREATE SEQUENCE `file_reference_seq` start with 1 minvalue 1 maxvalue 9223372036854775806 increment by 50 nocache nocycle ENGINE=InnoDB;
DO SETVAL(`file_reference_seq`, 1, 0);

--
-- Table structure for table `auction`
--

DROP TABLE IF EXISTS `auction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `auction` (
  `id` bigint(20) NOT NULL,
  `bid` bigint(20) DEFAULT NULL,
  `buyout` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `first_seen` datetime(6) DEFAULT NULL,
  `last_seen` datetime(6) DEFAULT NULL,
  `quantity` bigint(20) NOT NULL,
  `time_left` tinyint(4) NOT NULL CHECK (`time_left` between 0 and 3),
  `unit_price` bigint(20) DEFAULT NULL,
  `connected_realm_id` int(11) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  `update_history_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`connected_realm_id`,`id`),
  KEY `idx_auction_connected_realm_update_deleted` (`connected_realm_id`,`update_history_id`,`deleted_at`),
  KEY `idx_auction_item_realm_deleted_last_seen` (`item_id`,`connected_realm_id`,`deleted_at`,`last_seen`),
  KEY `idx_auction_deleted_at` (`deleted_at`),
  KEY `FK9u4k9bcxv8lujys69jp4cb96j` (`update_history_id`),
  CONSTRAINT `FK9u4k9bcxv8lujys69jp4cb96j` FOREIGN KEY (`update_history_id`) REFERENCES `connected_realm_update_history` (`id`),
  CONSTRAINT `FKeyvv5vuhvp1cdas36ejllgnbs` FOREIGN KEY (`connected_realm_id`) REFERENCES `connected_realm` (`id`),
  CONSTRAINT `FKg3o90pdpi57jqbms95ssexh9m` FOREIGN KEY (`item_id`) REFERENCES `auction_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auction_house`
--

DROP TABLE IF EXISTS `auction_house`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `auction_house` (
  `id` int(11) NOT NULL,
  `auto_update` bit(1) NOT NULL,
  `avg_delay` bigint(20) NOT NULL,
  `connected_id` int(11) NOT NULL,
  `game_build` int(11) NOT NULL,
  `highest_delay` bigint(20) NOT NULL,
  `last_daily_price_update` datetime(6) DEFAULT NULL,
  `last_history_delete_event` datetime(6) DEFAULT NULL,
  `last_history_delete_event_daily` datetime(6) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_requested` datetime(6) DEFAULT NULL,
  `last_stats_insert` datetime(6) DEFAULT NULL,
  `last_trend_update_initiation` datetime(6) DEFAULT NULL,
  `lowest_delay` bigint(20) NOT NULL,
  `next_update` datetime(6) DEFAULT NULL,
  `region` enum('Europe','Korea','NorthAmerica','Taiwan') DEFAULT NULL,
  `stats_last_modified` bigint(20) NOT NULL,
  `update_attempts` int(11) NOT NULL,
  `auction_file_id` bigint(20) DEFAULT NULL,
  `stats_file_id` bigint(20) DEFAULT NULL,
  `tsm_file_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnhr38kqa8xr1369919djx190q` (`auction_file_id`),
  KEY `FKpra6lx3dciaf45o7f0hsaidmj` (`stats_file_id`),
  KEY `FK1daac62mtf97e4fachppt4o0p` (`tsm_file_id`),
  CONSTRAINT `FK1daac62mtf97e4fachppt4o0p` FOREIGN KEY (`tsm_file_id`) REFERENCES `file_reference` (`id`),
  CONSTRAINT `FKnhr38kqa8xr1369919djx190q` FOREIGN KEY (`auction_file_id`) REFERENCES `file_reference` (`id`),
  CONSTRAINT `FKpra6lx3dciaf45o7f0hsaidmj` FOREIGN KEY (`stats_file_id`) REFERENCES `file_reference` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auction_house_file_log`
--

DROP TABLE IF EXISTS `auction_house_file_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `auction_house_file_log` (
  `id` bigint(20) NOT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `time_since_previous_dump` bigint(20) NOT NULL,
  `auction_house_id` int(11) NOT NULL,
  `file_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK77bxnsnf4ipimn0cvlnol4lgi` (`auction_house_id`),
  KEY `FK7j4v7vgbi15irb7kirb90lfl4` (`file_id`),
  CONSTRAINT `FK77bxnsnf4ipimn0cvlnol4lgi` FOREIGN KEY (`auction_house_id`) REFERENCES `auction_house` (`id`),
  CONSTRAINT `FK7j4v7vgbi15irb7kirb90lfl4` FOREIGN KEY (`file_id`) REFERENCES `file_reference` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auction_item`
--

DROP TABLE IF EXISTS `auction_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `auction_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bonus_lists` varchar(255) NOT NULL,
  `context` int(11) DEFAULT NULL,
  `item_id` int(11) NOT NULL,
  `pet_breed_id` int(11) DEFAULT NULL,
  `pet_level` int(11) DEFAULT NULL,
  `pet_quality_id` int(11) DEFAULT NULL,
  `pet_species_id` int(11) DEFAULT NULL,
  `variant_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auction_item_variant_hash` (`variant_hash`),
  KEY `idx_auction_item_item_id` (`item_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auction_item_modifier`
--

DROP TABLE IF EXISTS `auction_item_modifier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `auction_item_modifier` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(255) NOT NULL,
  `value` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auction_item_modifier_type_value` (`type`,`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auction_item_modifier_link`
--

DROP TABLE IF EXISTS `auction_item_modifier_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `auction_item_modifier_link` (
  `auction_item_id` bigint(20) NOT NULL,
  `modifier_id` bigint(20) NOT NULL,
  `sort_order` int(11) NOT NULL,
  PRIMARY KEY (`auction_item_id`,`sort_order`),
  KEY `FK1t3dqundqexxylwv3pkx0bh46` (`modifier_id`),
  CONSTRAINT `FK1t3dqundqexxylwv3pkx0bh46` FOREIGN KEY (`modifier_id`) REFERENCES `auction_item_modifier` (`id`),
  CONSTRAINT `FKqxh9621iu7rg4ckp27uyy6i0t` FOREIGN KEY (`auction_item_id`) REFERENCES `auction_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `connected_realm`
--

DROP TABLE IF EXISTS `connected_realm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `connected_realm` (
  `id` int(11) NOT NULL,
  `auction_house_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo47mg5tq1ch5am59kf06w8kje` (`auction_house_id`),
  CONSTRAINT `FKi9wy1ystlrtodjq5j3x777awt` FOREIGN KEY (`auction_house_id`) REFERENCES `auction_house` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `connected_realm_realms`
--

DROP TABLE IF EXISTS `connected_realm_realms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `connected_realm_realms` (
  `connected_realm_id` int(11) NOT NULL,
  `realms_id` int(11) NOT NULL,
  UNIQUE KEY `UKmdxvg19ueqio680o291rf6qp5` (`realms_id`),
  KEY `FKndefwupwjv269wdfkak40jrq6` (`connected_realm_id`),
  CONSTRAINT `FKj4kohpfn7b1ut8tngrgkw2frt` FOREIGN KEY (`realms_id`) REFERENCES `realm` (`id`),
  CONSTRAINT `FKndefwupwjv269wdfkak40jrq6` FOREIGN KEY (`connected_realm_id`) REFERENCES `connected_realm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `connected_realm_update_history`
--

DROP TABLE IF EXISTS `connected_realm_update_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `connected_realm_update_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `auction_count` int(11) NOT NULL,
  `completed_timestamp` datetime(6) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `update_timestamp` datetime(6) DEFAULT NULL,
  `connected_realm_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cruh_connected_realm_id_last_modified` (`connected_realm_id`,`last_modified`),
  CONSTRAINT `FKcfumf900nbx3nkblkygc7mlcm` FOREIGN KEY (`connected_realm_id`) REFERENCES `connected_realm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `daily_auction_stats`
--

DROP TABLE IF EXISTS `daily_auction_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `daily_auction_stats` (
  `bonus_key` varchar(255) NOT NULL,
  `connected_realm_id` int(11) NOT NULL,
  `date` date NOT NULL,
  `item_id` int(11) NOT NULL,
  `modifier_key` varchar(255) NOT NULL,
  `pet_species_id` int(11) NOT NULL,
  `avg01` bigint(20) DEFAULT NULL,
  `avg02` bigint(20) DEFAULT NULL,
  `avg03` bigint(20) DEFAULT NULL,
  `avg04` bigint(20) DEFAULT NULL,
  `avg05` bigint(20) DEFAULT NULL,
  `avg06` bigint(20) DEFAULT NULL,
  `avg07` bigint(20) DEFAULT NULL,
  `avg08` bigint(20) DEFAULT NULL,
  `avg09` bigint(20) DEFAULT NULL,
  `avg10` bigint(20) DEFAULT NULL,
  `avg11` bigint(20) DEFAULT NULL,
  `avg12` bigint(20) DEFAULT NULL,
  `avg13` bigint(20) DEFAULT NULL,
  `avg14` bigint(20) DEFAULT NULL,
  `avg15` bigint(20) DEFAULT NULL,
  `avg16` bigint(20) DEFAULT NULL,
  `avg17` bigint(20) DEFAULT NULL,
  `avg18` bigint(20) DEFAULT NULL,
  `avg19` bigint(20) DEFAULT NULL,
  `avg20` bigint(20) DEFAULT NULL,
  `avg21` bigint(20) DEFAULT NULL,
  `avg22` bigint(20) DEFAULT NULL,
  `avg23` bigint(20) DEFAULT NULL,
  `avg24` bigint(20) DEFAULT NULL,
  `avg25` bigint(20) DEFAULT NULL,
  `avg26` bigint(20) DEFAULT NULL,
  `avg27` bigint(20) DEFAULT NULL,
  `avg28` bigint(20) DEFAULT NULL,
  `avg29` bigint(20) DEFAULT NULL,
  `avg30` bigint(20) DEFAULT NULL,
  `avg31` bigint(20) DEFAULT NULL,
  `avg_quantity01` bigint(20) DEFAULT NULL,
  `avg_quantity02` bigint(20) DEFAULT NULL,
  `avg_quantity03` bigint(20) DEFAULT NULL,
  `avg_quantity04` bigint(20) DEFAULT NULL,
  `avg_quantity05` bigint(20) DEFAULT NULL,
  `avg_quantity06` bigint(20) DEFAULT NULL,
  `avg_quantity07` bigint(20) DEFAULT NULL,
  `avg_quantity08` bigint(20) DEFAULT NULL,
  `avg_quantity09` bigint(20) DEFAULT NULL,
  `avg_quantity10` bigint(20) DEFAULT NULL,
  `avg_quantity11` bigint(20) DEFAULT NULL,
  `avg_quantity12` bigint(20) DEFAULT NULL,
  `avg_quantity13` bigint(20) DEFAULT NULL,
  `avg_quantity14` bigint(20) DEFAULT NULL,
  `avg_quantity15` bigint(20) DEFAULT NULL,
  `avg_quantity16` bigint(20) DEFAULT NULL,
  `avg_quantity17` bigint(20) DEFAULT NULL,
  `avg_quantity18` bigint(20) DEFAULT NULL,
  `avg_quantity19` bigint(20) DEFAULT NULL,
  `avg_quantity20` bigint(20) DEFAULT NULL,
  `avg_quantity21` bigint(20) DEFAULT NULL,
  `avg_quantity22` bigint(20) DEFAULT NULL,
  `avg_quantity23` bigint(20) DEFAULT NULL,
  `avg_quantity24` bigint(20) DEFAULT NULL,
  `avg_quantity25` bigint(20) DEFAULT NULL,
  `avg_quantity26` bigint(20) DEFAULT NULL,
  `avg_quantity27` bigint(20) DEFAULT NULL,
  `avg_quantity28` bigint(20) DEFAULT NULL,
  `avg_quantity29` bigint(20) DEFAULT NULL,
  `avg_quantity30` bigint(20) DEFAULT NULL,
  `avg_quantity31` bigint(20) DEFAULT NULL,
  `max01` bigint(20) DEFAULT NULL,
  `max02` bigint(20) DEFAULT NULL,
  `max03` bigint(20) DEFAULT NULL,
  `max04` bigint(20) DEFAULT NULL,
  `max05` bigint(20) DEFAULT NULL,
  `max06` bigint(20) DEFAULT NULL,
  `max07` bigint(20) DEFAULT NULL,
  `max08` bigint(20) DEFAULT NULL,
  `max09` bigint(20) DEFAULT NULL,
  `max10` bigint(20) DEFAULT NULL,
  `max11` bigint(20) DEFAULT NULL,
  `max12` bigint(20) DEFAULT NULL,
  `max13` bigint(20) DEFAULT NULL,
  `max14` bigint(20) DEFAULT NULL,
  `max15` bigint(20) DEFAULT NULL,
  `max16` bigint(20) DEFAULT NULL,
  `max17` bigint(20) DEFAULT NULL,
  `max18` bigint(20) DEFAULT NULL,
  `max19` bigint(20) DEFAULT NULL,
  `max20` bigint(20) DEFAULT NULL,
  `max21` bigint(20) DEFAULT NULL,
  `max22` bigint(20) DEFAULT NULL,
  `max23` bigint(20) DEFAULT NULL,
  `max24` bigint(20) DEFAULT NULL,
  `max25` bigint(20) DEFAULT NULL,
  `max26` bigint(20) DEFAULT NULL,
  `max27` bigint(20) DEFAULT NULL,
  `max28` bigint(20) DEFAULT NULL,
  `max29` bigint(20) DEFAULT NULL,
  `max30` bigint(20) DEFAULT NULL,
  `max31` bigint(20) DEFAULT NULL,
  `max_quantity01` bigint(20) DEFAULT NULL,
  `max_quantity02` bigint(20) DEFAULT NULL,
  `max_quantity03` bigint(20) DEFAULT NULL,
  `max_quantity04` bigint(20) DEFAULT NULL,
  `max_quantity05` bigint(20) DEFAULT NULL,
  `max_quantity06` bigint(20) DEFAULT NULL,
  `max_quantity07` bigint(20) DEFAULT NULL,
  `max_quantity08` bigint(20) DEFAULT NULL,
  `max_quantity09` bigint(20) DEFAULT NULL,
  `max_quantity10` bigint(20) DEFAULT NULL,
  `max_quantity11` bigint(20) DEFAULT NULL,
  `max_quantity12` bigint(20) DEFAULT NULL,
  `max_quantity13` bigint(20) DEFAULT NULL,
  `max_quantity14` bigint(20) DEFAULT NULL,
  `max_quantity15` bigint(20) DEFAULT NULL,
  `max_quantity16` bigint(20) DEFAULT NULL,
  `max_quantity17` bigint(20) DEFAULT NULL,
  `max_quantity18` bigint(20) DEFAULT NULL,
  `max_quantity19` bigint(20) DEFAULT NULL,
  `max_quantity20` bigint(20) DEFAULT NULL,
  `max_quantity21` bigint(20) DEFAULT NULL,
  `max_quantity22` bigint(20) DEFAULT NULL,
  `max_quantity23` bigint(20) DEFAULT NULL,
  `max_quantity24` bigint(20) DEFAULT NULL,
  `max_quantity25` bigint(20) DEFAULT NULL,
  `max_quantity26` bigint(20) DEFAULT NULL,
  `max_quantity27` bigint(20) DEFAULT NULL,
  `max_quantity28` bigint(20) DEFAULT NULL,
  `max_quantity29` bigint(20) DEFAULT NULL,
  `max_quantity30` bigint(20) DEFAULT NULL,
  `max_quantity31` bigint(20) DEFAULT NULL,
  `min01` bigint(20) DEFAULT NULL,
  `min02` bigint(20) DEFAULT NULL,
  `min03` bigint(20) DEFAULT NULL,
  `min04` bigint(20) DEFAULT NULL,
  `min05` bigint(20) DEFAULT NULL,
  `min06` bigint(20) DEFAULT NULL,
  `min07` bigint(20) DEFAULT NULL,
  `min08` bigint(20) DEFAULT NULL,
  `min09` bigint(20) DEFAULT NULL,
  `min10` bigint(20) DEFAULT NULL,
  `min11` bigint(20) DEFAULT NULL,
  `min12` bigint(20) DEFAULT NULL,
  `min13` bigint(20) DEFAULT NULL,
  `min14` bigint(20) DEFAULT NULL,
  `min15` bigint(20) DEFAULT NULL,
  `min16` bigint(20) DEFAULT NULL,
  `min17` bigint(20) DEFAULT NULL,
  `min18` bigint(20) DEFAULT NULL,
  `min19` bigint(20) DEFAULT NULL,
  `min20` bigint(20) DEFAULT NULL,
  `min21` bigint(20) DEFAULT NULL,
  `min22` bigint(20) DEFAULT NULL,
  `min23` bigint(20) DEFAULT NULL,
  `min24` bigint(20) DEFAULT NULL,
  `min25` bigint(20) DEFAULT NULL,
  `min26` bigint(20) DEFAULT NULL,
  `min27` bigint(20) DEFAULT NULL,
  `min28` bigint(20) DEFAULT NULL,
  `min29` bigint(20) DEFAULT NULL,
  `min30` bigint(20) DEFAULT NULL,
  `min31` bigint(20) DEFAULT NULL,
  `min_hour01` smallint(6) DEFAULT NULL,
  `min_hour02` smallint(6) DEFAULT NULL,
  `min_hour03` smallint(6) DEFAULT NULL,
  `min_hour04` smallint(6) DEFAULT NULL,
  `min_hour05` smallint(6) DEFAULT NULL,
  `min_hour06` smallint(6) DEFAULT NULL,
  `min_hour07` smallint(6) DEFAULT NULL,
  `min_hour08` smallint(6) DEFAULT NULL,
  `min_hour09` smallint(6) DEFAULT NULL,
  `min_hour10` smallint(6) DEFAULT NULL,
  `min_hour11` smallint(6) DEFAULT NULL,
  `min_hour12` smallint(6) DEFAULT NULL,
  `min_hour13` smallint(6) DEFAULT NULL,
  `min_hour14` smallint(6) DEFAULT NULL,
  `min_hour15` smallint(6) DEFAULT NULL,
  `min_hour16` smallint(6) DEFAULT NULL,
  `min_hour17` smallint(6) DEFAULT NULL,
  `min_hour18` smallint(6) DEFAULT NULL,
  `min_hour19` smallint(6) DEFAULT NULL,
  `min_hour20` smallint(6) DEFAULT NULL,
  `min_hour21` smallint(6) DEFAULT NULL,
  `min_hour22` smallint(6) DEFAULT NULL,
  `min_hour23` smallint(6) DEFAULT NULL,
  `min_hour24` smallint(6) DEFAULT NULL,
  `min_hour25` smallint(6) DEFAULT NULL,
  `min_hour26` smallint(6) DEFAULT NULL,
  `min_hour27` smallint(6) DEFAULT NULL,
  `min_hour28` smallint(6) DEFAULT NULL,
  `min_hour29` smallint(6) DEFAULT NULL,
  `min_hour30` smallint(6) DEFAULT NULL,
  `min_hour31` smallint(6) DEFAULT NULL,
  `min_quantity01` bigint(20) DEFAULT NULL,
  `min_quantity02` bigint(20) DEFAULT NULL,
  `min_quantity03` bigint(20) DEFAULT NULL,
  `min_quantity04` bigint(20) DEFAULT NULL,
  `min_quantity05` bigint(20) DEFAULT NULL,
  `min_quantity06` bigint(20) DEFAULT NULL,
  `min_quantity07` bigint(20) DEFAULT NULL,
  `min_quantity08` bigint(20) DEFAULT NULL,
  `min_quantity09` bigint(20) DEFAULT NULL,
  `min_quantity10` bigint(20) DEFAULT NULL,
  `min_quantity11` bigint(20) DEFAULT NULL,
  `min_quantity12` bigint(20) DEFAULT NULL,
  `min_quantity13` bigint(20) DEFAULT NULL,
  `min_quantity14` bigint(20) DEFAULT NULL,
  `min_quantity15` bigint(20) DEFAULT NULL,
  `min_quantity16` bigint(20) DEFAULT NULL,
  `min_quantity17` bigint(20) DEFAULT NULL,
  `min_quantity18` bigint(20) DEFAULT NULL,
  `min_quantity19` bigint(20) DEFAULT NULL,
  `min_quantity20` bigint(20) DEFAULT NULL,
  `min_quantity21` bigint(20) DEFAULT NULL,
  `min_quantity22` bigint(20) DEFAULT NULL,
  `min_quantity23` bigint(20) DEFAULT NULL,
  `min_quantity24` bigint(20) DEFAULT NULL,
  `min_quantity25` bigint(20) DEFAULT NULL,
  `min_quantity26` bigint(20) DEFAULT NULL,
  `min_quantity27` bigint(20) DEFAULT NULL,
  `min_quantity28` bigint(20) DEFAULT NULL,
  `min_quantity29` bigint(20) DEFAULT NULL,
  `min_quantity30` bigint(20) DEFAULT NULL,
  `min_quantity31` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`bonus_key`,`connected_realm_id`,`date`,`item_id`,`modifier_key`,`pet_species_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_reference`
--

DROP TABLE IF EXISTS `file_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_reference` (
  `id` bigint(20) NOT NULL,
  `bucket_name` varchar(255) DEFAULT NULL,
  `created` datetime(6) DEFAULT NULL,
  `path` varchar(255) DEFAULT NULL,
  `size` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hourly_auction_stats`
--

DROP TABLE IF EXISTS `hourly_auction_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `hourly_auction_stats` (
  `bonus_key` varchar(255) NOT NULL,
  `connected_realm_id` int(11) NOT NULL,
  `date` date NOT NULL,
  `item_id` int(11) NOT NULL,
  `modifier_key` varchar(255) NOT NULL,
  `pet_species_id` int(11) NOT NULL,
  `price00` bigint(20) DEFAULT NULL,
  `price01` bigint(20) DEFAULT NULL,
  `price02` bigint(20) DEFAULT NULL,
  `price03` bigint(20) DEFAULT NULL,
  `price04` bigint(20) DEFAULT NULL,
  `price05` bigint(20) DEFAULT NULL,
  `price06` bigint(20) DEFAULT NULL,
  `price07` bigint(20) DEFAULT NULL,
  `price08` bigint(20) DEFAULT NULL,
  `price09` bigint(20) DEFAULT NULL,
  `price10` bigint(20) DEFAULT NULL,
  `price11` bigint(20) DEFAULT NULL,
  `price12` bigint(20) DEFAULT NULL,
  `price13` bigint(20) DEFAULT NULL,
  `price14` bigint(20) DEFAULT NULL,
  `price15` bigint(20) DEFAULT NULL,
  `price16` bigint(20) DEFAULT NULL,
  `price17` bigint(20) DEFAULT NULL,
  `price18` bigint(20) DEFAULT NULL,
  `price19` bigint(20) DEFAULT NULL,
  `price20` bigint(20) DEFAULT NULL,
  `price21` bigint(20) DEFAULT NULL,
  `price22` bigint(20) DEFAULT NULL,
  `price23` bigint(20) DEFAULT NULL,
  `quantity00` bigint(20) DEFAULT NULL,
  `quantity01` bigint(20) DEFAULT NULL,
  `quantity02` bigint(20) DEFAULT NULL,
  `quantity03` bigint(20) DEFAULT NULL,
  `quantity04` bigint(20) DEFAULT NULL,
  `quantity05` bigint(20) DEFAULT NULL,
  `quantity06` bigint(20) DEFAULT NULL,
  `quantity07` bigint(20) DEFAULT NULL,
  `quantity08` bigint(20) DEFAULT NULL,
  `quantity09` bigint(20) DEFAULT NULL,
  `quantity10` bigint(20) DEFAULT NULL,
  `quantity11` bigint(20) DEFAULT NULL,
  `quantity12` bigint(20) DEFAULT NULL,
  `quantity13` bigint(20) DEFAULT NULL,
  `quantity14` bigint(20) DEFAULT NULL,
  `quantity15` bigint(20) DEFAULT NULL,
  `quantity16` bigint(20) DEFAULT NULL,
  `quantity17` bigint(20) DEFAULT NULL,
  `quantity18` bigint(20) DEFAULT NULL,
  `quantity19` bigint(20) DEFAULT NULL,
  `quantity20` bigint(20) DEFAULT NULL,
  `quantity21` bigint(20) DEFAULT NULL,
  `quantity22` bigint(20) DEFAULT NULL,
  `quantity23` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`bonus_key`,`connected_realm_id`,`date`,`item_id`,`modifier_key`,`pet_species_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_type`
--

DROP TABLE IF EXISTS `inventory_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_type` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(255) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `uk_inventory_type_type` (`type`),
  UNIQUE KEY `UKkwticwb7st6ukk3a41ibijdty` (`name_id`),
  CONSTRAINT `FKsefvyw0qxcs4tfc4cd7pgawsi` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item`
--

DROP TABLE IF EXISTS `item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item` (
  `id` int(11) NOT NULL,
  `is_equippable` bit(1) NOT NULL,
  `is_stackable` bit(1) NOT NULL,
  `level` int(11) NOT NULL,
  `max_count` int(11) NOT NULL,
  `media_url` varchar(255) DEFAULT NULL,
  `purchase_price` int(11) NOT NULL,
  `purchase_quantity` int(11) NOT NULL,
  `required_level` int(11) NOT NULL,
  `sell_price` int(11) NOT NULL,
  `binding_id` bigint(20) DEFAULT NULL,
  `inventory_type_id` bigint(20) DEFAULT NULL,
  `item_class_id` int(11) DEFAULT NULL,
  `item_subclass_id` bigint(20) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  `quality_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcgrg884xedtb5d2ysbg4qis7d` (`name_id`),
  KEY `FKalhf1x135bxsocv2v8qank41v` (`binding_id`),
  KEY `FKltrvw3dcf45v1vhwnmwdajqoe` (`inventory_type_id`),
  KEY `FKp7d89oq4krdbc5nb625qotntr` (`item_class_id`),
  KEY `FKhfx1tk6g4a57344ucibipu2m8` (`item_subclass_id`),
  KEY `FK7e2xfqcf0y11ssfvkxc9lg4d1` (`quality_id`),
  CONSTRAINT `FK7e2xfqcf0y11ssfvkxc9lg4d1` FOREIGN KEY (`quality_id`) REFERENCES `item_quality` (`internal_id`),
  CONSTRAINT `FKalhf1x135bxsocv2v8qank41v` FOREIGN KEY (`binding_id`) REFERENCES `item_binding` (`internal_id`),
  CONSTRAINT `FKhfx1tk6g4a57344ucibipu2m8` FOREIGN KEY (`item_subclass_id`) REFERENCES `item_subclass` (`internal_id`),
  CONSTRAINT `FKltrvw3dcf45v1vhwnmwdajqoe` FOREIGN KEY (`inventory_type_id`) REFERENCES `inventory_type` (`internal_id`),
  CONSTRAINT `FKm4xcddqh0s1ctisituyuln77q` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FKp7d89oq4krdbc5nb625qotntr` FOREIGN KEY (`item_class_id`) REFERENCES `item_class` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_appearance`
--

DROP TABLE IF EXISTS `item_appearance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_appearance` (
  `id` int(11) NOT NULL,
  `item_display_info_id` int(11) NOT NULL,
  `media_url` varchar(255) DEFAULT NULL,
  `item_class_id` int(11) DEFAULT NULL,
  `item_subclass_internal_id` bigint(20) DEFAULT NULL,
  `slot_internal_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd28g76rodbwtgexw1350mxh2i` (`item_class_id`),
  UNIQUE KEY `UK1h4eh8xrck27gkbsau541b6fx` (`item_subclass_internal_id`),
  UNIQUE KEY `UK3itb4795428uconhfa9dvfnjm` (`slot_internal_id`),
  CONSTRAINT `FK5kwj4wyo9rw551pufoe5rg0uh` FOREIGN KEY (`item_subclass_internal_id`) REFERENCES `item_subclass` (`internal_id`),
  CONSTRAINT `FKmmunya697ygwv2s8uhwqm9vjc` FOREIGN KEY (`item_class_id`) REFERENCES `item_class` (`id`),
  CONSTRAINT `FKp39q3809o5p27ogh8ildy49dh` FOREIGN KEY (`slot_internal_id`) REFERENCES `inventory_type` (`internal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_appearance_ref`
--

DROP TABLE IF EXISTS `item_appearance_ref`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_appearance_ref` (
  `id` int(11) NOT NULL,
  `href` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_appearance_refs`
--

DROP TABLE IF EXISTS `item_appearance_refs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_appearance_refs` (
  `item_id` int(11) NOT NULL,
  `appearance_ref_id` int(11) NOT NULL,
  UNIQUE KEY `uk_item_appearance_ref_pair` (`item_id`,`appearance_ref_id`),
  UNIQUE KEY `UKah3jf70jupn9yg8e17iqfjf7k` (`appearance_ref_id`),
  CONSTRAINT `FKm19fkby7qu7os24ycnvet9xga` FOREIGN KEY (`appearance_ref_id`) REFERENCES `item_appearance_ref` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_binding`
--

DROP TABLE IF EXISTS `item_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_binding` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(255) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `uk_item_binding_type` (`type`),
  UNIQUE KEY `UK8v649gx5yyeiiibdth7j5lnr3` (`name_id`),
  CONSTRAINT `FKfxvcca43muqkcfql1euue45u7` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_class`
--

DROP TABLE IF EXISTS `item_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_class` (
  `id` int(11) NOT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3vxpe8tv78bffi4dyo55pyrv1` (`name_id`),
  CONSTRAINT `FKntf1l76h7wf083j7i5p9rnfa` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_quality`
--

DROP TABLE IF EXISTS `item_quality`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_quality` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(255) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `uk_item_quality_type` (`type`),
  UNIQUE KEY `UKarmcvv72tgq1gy7tx4rpgejvt` (`name_id`),
  CONSTRAINT `FKqauaw5lbymkcs05mhxcw64t3d` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_subclass`
--

DROP TABLE IF EXISTS `item_subclass`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_subclass` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `class_id` int(11) NOT NULL,
  `hide_subclass_in_tooltips` bit(1) DEFAULT NULL,
  `subclass_id` int(11) NOT NULL,
  `display_name_id` bigint(20) DEFAULT NULL,
  `item_class_owner_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `uk_item_subclass_key` (`class_id`,`subclass_id`),
  UNIQUE KEY `UKaahjh6k8jtftu82a9n2bqjwpj` (`display_name_id`),
  KEY `FKwbp3w58orewwhnyna411ki80` (`item_class_owner_id`),
  CONSTRAINT `FKkeq83lv9twiqudoimpj5v54i7` FOREIGN KEY (`display_name_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FKwbp3w58orewwhnyna411ki80` FOREIGN KEY (`item_class_owner_id`) REFERENCES `item_class` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_summary`
--

DROP TABLE IF EXISTS `item_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_summary` (
  `id` int(11) NOT NULL,
  `href` varchar(255) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  `item_appearance_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9k448sywod06a2hsgmn6buh9l` (`name_id`),
  KEY `FKfuj4eomjedg9sf9cplou3cdy` (`item_appearance_id`),
  CONSTRAINT `FKfuj4eomjedg9sf9cplou3cdy` FOREIGN KEY (`item_appearance_id`) REFERENCES `item_appearance` (`id`),
  CONSTRAINT `FKmdfkp056b5obfbp385n5gbdfa` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `locale`
--

DROP TABLE IF EXISTS `locale`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `locale` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `de_de` text DEFAULT NULL,
  `en_gb` text DEFAULT NULL,
  `en_us` text DEFAULT NULL,
  `es_es` text DEFAULT NULL,
  `es_mx` text DEFAULT NULL,
  `fr_fr` text DEFAULT NULL,
  `it_it` text DEFAULT NULL,
  `ko_kr` text DEFAULT NULL,
  `pt_br` text DEFAULT NULL,
  `pt_pt` text DEFAULT NULL,
  `ru_ru` text DEFAULT NULL,
  `source_field` varchar(255) NOT NULL,
  `source_key` varchar(255) NOT NULL,
  `source_type` varchar(255) NOT NULL,
  `zh_cn` text DEFAULT NULL,
  `zh_tw` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_locale_source` (`source_type`,`source_key`,`source_field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modified_crafting_category`
--

DROP TABLE IF EXISTS `modified_crafting_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `modified_crafting_category` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_id` int(11) NOT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  `modified_crafting_slot_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `UKr5n1y6a3wkhq2are8yw2wosm3` (`name_id`),
  KEY `FKo4tp896y233t4filxye3na1ui` (`modified_crafting_slot_id`),
  CONSTRAINT `FKd5htebohgoics8pc99lsf21oe` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FKo4tp896y233t4filxye3na1ui` FOREIGN KEY (`modified_crafting_slot_id`) REFERENCES `modified_crafting_slot` (`internal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modified_crafting_category_metadata`
--

DROP TABLE IF EXISTS `modified_crafting_category_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `modified_crafting_category_metadata` (
  `id` int(11) NOT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhmfq3dg4pqcal8rut51pgkrg5` (`name_id`),
  CONSTRAINT `FK7km2mvwrjrxpidur6xnegpvty` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modified_crafting_slot`
--

DROP TABLE IF EXISTS `modified_crafting_slot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `modified_crafting_slot` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `display_order` int(11) DEFAULT NULL,
  `slot_type_id` int(11) NOT NULL,
  `description_id` bigint(20) DEFAULT NULL,
  `recipe_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `UKcb5m8gfirapete6hxaj04tgqk` (`description_id`),
  KEY `FK27jyk3ij3eaqwhcw83o9vrnot` (`recipe_id`),
  CONSTRAINT `FK11b76mim7j1u15j6c4sqjgim0` FOREIGN KEY (`description_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FK27jyk3ij3eaqwhcw83o9vrnot` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modified_crafting_slot_metadata`
--

DROP TABLE IF EXISTS `modified_crafting_slot_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `modified_crafting_slot_metadata` (
  `id` int(11) NOT NULL,
  `description_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbxlc46vhbb2a3hcxypd30x6xv` (`description_id`),
  CONSTRAINT `FKj6k9t8e0qaw1626s2r066e4d7` FOREIGN KEY (`description_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modified_crafting_slot_metadata_category`
--

DROP TABLE IF EXISTS `modified_crafting_slot_metadata_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `modified_crafting_slot_metadata_category` (
  `slot_id` int(11) NOT NULL,
  `category_id` int(11) DEFAULT NULL,
  KEY `FKfdhr23paxgx1le78dwtnk6wri` (`slot_id`),
  CONSTRAINT `FKfdhr23paxgx1le78dwtnk6wri` FOREIGN KEY (`slot_id`) REFERENCES `modified_crafting_slot_metadata` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `profession`
--

DROP TABLE IF EXISTS `profession`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `profession` (
  `id` int(11) NOT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `media_url` varchar(255) DEFAULT NULL,
  `description_id` bigint(20) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsjhet4y345crv31aq7h4cpowa` (`description_id`),
  UNIQUE KEY `UKgsy2u8r7a5rt1xt3sk3x7ixwf` (`name_id`),
  CONSTRAINT `FK8nheektgvwihjjwisa3gsaicl` FOREIGN KEY (`description_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FKixanyj3h6ipyssbu9dfj9qi13` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `profession_category`
--

DROP TABLE IF EXISTS `profession_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `profession_category` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name_id` bigint(20) DEFAULT NULL,
  `skill_tier_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `UK55tk2lttf86ym2yhvhu3v7e9p` (`name_id`),
  KEY `FK1e5v73lcnyjb78upo08dj4l1` (`skill_tier_id`),
  CONSTRAINT `FK1e5v73lcnyjb78upo08dj4l1` FOREIGN KEY (`skill_tier_id`) REFERENCES `skill_tier` (`id`),
  CONSTRAINT `FK6ocyv5c6gv0dm0ccwpnhh6ee3` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `realm`
--

DROP TABLE IF EXISTS `realm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `realm` (
  `id` int(11) NOT NULL,
  `category` varchar(255) DEFAULT NULL,
  `game_build` tinyint(4) DEFAULT NULL CHECK (`game_build` between 0 and 1),
  `locale` tinyint(4) DEFAULT NULL CHECK (`locale` between 0 and 12),
  `name` varchar(255) DEFAULT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `timezone` varchar(255) DEFAULT NULL,
  `region_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5g7crwuqanovsstoyubnsm7jg` (`region_id`),
  CONSTRAINT `FK5g7crwuqanovsstoyubnsm7jg` FOREIGN KEY (`region_id`) REFERENCES `region` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recipe`
--

DROP TABLE IF EXISTS `recipe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `recipe` (
  `id` int(11) NOT NULL,
  `crafted_item_id` int(11) DEFAULT NULL,
  `crafted_quantity` int(11) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `media_url` varchar(255) DEFAULT NULL,
  `rank` int(11) DEFAULT NULL,
  `description_id` bigint(20) DEFAULT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  `profession_category_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj5a48e5qk1agutsdwdeu3ifoq` (`description_id`),
  UNIQUE KEY `UKfsqhfjnj40quo0h1b8xhvkxpw` (`name_id`),
  KEY `FKjdgqyc7wwcwdiar63bre3u3dv` (`profession_category_id`),
  CONSTRAINT `FK3c3bng447hlm026ok906rugws` FOREIGN KEY (`description_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FKi43tcnav002ykcnfy2476jv4c` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`),
  CONSTRAINT `FKjdgqyc7wwcwdiar63bre3u3dv` FOREIGN KEY (`profession_category_id`) REFERENCES `profession_category` (`internal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recipe_reagent`
--

DROP TABLE IF EXISTS `recipe_reagent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `recipe_reagent` (
  `internal_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `item_id` int(11) NOT NULL,
  `quantity` int(11) NOT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  `recipe_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`internal_id`),
  UNIQUE KEY `UKijuu0en5r85ybhflyeti97lwg` (`name_id`),
  KEY `FKjtyx64w8r7k755pweumybcctk` (`recipe_id`),
  CONSTRAINT `FKjtyx64w8r7k755pweumybcctk` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`),
  CONSTRAINT `FKql147ewcxqod1q7vg0iudnecw` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `region`
--

DROP TABLE IF EXISTS `region`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `region` (
  `id` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `type` tinyint(4) DEFAULT NULL CHECK (`type` between 0 and 3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skill_tier`
--

DROP TABLE IF EXISTS `skill_tier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `skill_tier` (
  `id` int(11) NOT NULL,
  `maximum_skill_level` int(11) NOT NULL,
  `minimum_skill_level` int(11) NOT NULL,
  `name_id` bigint(20) DEFAULT NULL,
  `profession_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtiapdl9sfmmq3o1slhyscef3j` (`name_id`),
  KEY `FK511dco52x4vrhaa1d3529w2pp` (`profession_id`),
  CONSTRAINT `FK511dco52x4vrhaa1d3529w2pp` FOREIGN KEY (`profession_id`) REFERENCES `profession` (`id`),
  CONSTRAINT `FK54irddcwjefx5u5xiewfbtxgt` FOREIGN KEY (`name_id`) REFERENCES `locale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
