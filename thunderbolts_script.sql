-- phpMyAdmin SQL Dump
-- version 4.0.4.1
-- http://www.phpmyadmin.net
--
-- Host: 127.0.0.1
-- Generation Time: Oct 24, 2013 at 12:24 AM
-- Server version: 5.5.32
-- PHP Version: 5.4.16

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `thunderbolts`
--
CREATE DATABASE IF NOT EXISTS `thunderbolts` DEFAULT CHARACTER SET latin1 COLLATE latin1_swedish_ci;
USE `thunderbolts`;

-- --------------------------------------------------------

--
-- Table structure for table `document`
--

CREATE TABLE `document` (
  `DocumentName` varchar(250) NOT NULL,
  `NamespaceName` varchar(250) NOT NULL DEFAULT 'EMPTY',
  `Owner` varchar(250) NOT NULL,
  `ReplicatedNode` varchar(250) DEFAULT NULL,
  `PreviousReplicatedNode` varchar(250) DEFAULT NULL,
  `ReplicationCount` int(3) NOT NULL DEFAULT '0' ,
  `IsReplicated` tinyint(1) DEFAULT NULL,
  `ToBeReplicated` tinyint(1) DEFAULT '1',
  `modifiedTS` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `C_DOCUMENT_NAMESPACE` (`DocumentName`,`NamespaceName`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;