CREATE TABLE IF NOT EXISTS `bestiaryloot`
(
    `ID`       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `npcID`    int(10) DEFAULT NULL,
    `itemID`   int(10) DEFAULT NULL,
    `playerID` int(10) DEFAULT NULL,
    `amount`   bigint  DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS `bestiaryloot_player_npc_item`
    ON `bestiaryloot` (`playerID`, `npcID`, `itemID`);
