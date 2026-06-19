CREATE TABLE IF NOT EXISTS `bestiaryloot`
(
    `ID`       int(10) NOT NULL AUTO_INCREMENT,
    `npcID`    int(10) DEFAULT NULL,
    `itemID`   int(10) DEFAULT NULL,
    `playerID` int(10) DEFAULT NULL,
    `amount`   bigint  DEFAULT 0,
    PRIMARY KEY (`ID`),
    UNIQUE KEY `bestiaryloot_player_npc_item` (`playerID`, `npcID`, `itemID`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
