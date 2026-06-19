UPDATE players
SET haircolour = CASE
	WHEN haircolour = 0 THEN 14
	WHEN haircolour = 1 THEN 13
	WHEN haircolour = 2 THEN 14
	WHEN haircolour IN (3, 4) THEN 17
	WHEN haircolour IN (5, 6) THEN 12
	WHEN haircolour = 7 THEN 11
	WHEN haircolour = 8 THEN 15
	WHEN haircolour = 9 THEN 16
	ELSE 10
END
WHERE haircolour < 10 OR haircolour > 17;
