-- SQLite VARCHAR lengths are advisory and existing values already have TEXT affinity.
-- Keep a recorded cross-engine migration while fresh schemas declare the column as TEXT.
SELECT 1;
