-- --------------------------------------------------------
-- Host:                         localhost
-- Server version:               10.3.21-MariaDB - Source distribution
-- Server OS:                    Linux
-- HeidiSQL Version:             11.2.0.6213
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Dumping database structure for main
CREATE DATABASE IF NOT EXISTS `main` /*!40100 DEFAULT CHARACTER SET utf8 */;
USE `main`;

-- Dumping structure for table main.registered_users
CREATE TABLE IF NOT EXISTS `registered_users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `auth_level` int(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Data exporting was unselected.


-- Dumping database structure for gameserver_sql
CREATE DATABASE IF NOT EXISTS `gameserver_sql` /*!40100 DEFAULT CHARACTER SET utf8 COLLATE utf8_unicode_ci */;
USE `gameserver_sql`;

-- Dumping structure for table gameserver_sql.games
CREATE TABLE IF NOT EXISTS `games` (
  `game_id` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `host_id` int(11) unsigned DEFAULT NULL,
  `last_access` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`game_id`),
  UNIQUE KEY `host_id` (`host_id`) USING BTREE,
  CONSTRAINT `FK_games_users` FOREIGN KEY (`host_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Data exporting was unselected.

-- Dumping structure for table gameserver_sql.game_data
CREATE TABLE IF NOT EXISTS `game_data` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `game_id` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `name` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `value` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `game_id_key` (`game_id`,`name`),
  CONSTRAINT `FK_game_data_games` FOREIGN KEY (`game_id`) REFERENCES `games` (`game_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Data exporting was unselected.

-- Dumping structure for table gameserver_sql.players
CREATE TABLE IF NOT EXISTS `players` (
  `user_id` int(11) unsigned NOT NULL,
  `game_id` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`user_id`),
  KEY `game_id` (`game_id`),
  CONSTRAINT `FK_players_games` FOREIGN KEY (`game_id`) REFERENCES `games` (`game_id`) ON UPDATE CASCADE,
  CONSTRAINT `FK_players_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Data exporting was unselected.

-- Dumping structure for table gameserver_sql.player_data
CREATE TABLE IF NOT EXISTS `player_data` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) unsigned NOT NULL,
  `name` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `value` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id_name` (`user_id`,`name`),
  CONSTRAINT `FK_player_data_players` FOREIGN KEY (`user_id`) REFERENCES `players` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=349 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Data exporting was unselected.

-- Dumping structure for table gameserver_sql.users
CREATE TABLE IF NOT EXISTS `users` (
  `user_id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `session_id` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `username` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `last_access` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `session_id` (`session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=639 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Data exporting was unselected.

/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
