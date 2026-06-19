UPDATE `players`
SET `haircolour` = 9 + `hairstyle`
WHERE `hairstyle` BETWEEN 1 AND 8
  AND `haircolour` BETWEEN 0 AND 9;

UPDATE `players`
SET `hairstyle` = 1
WHERE `hairstyle` > 1;
