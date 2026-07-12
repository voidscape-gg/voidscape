#!/usr/bin/env bash
# Compiles and runs deterministic fault injection against subscription-card transactions.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
SOURCE="$REPO/server/src/com/openrsc/server/content/SubscriptionCardTransactions.java"
SUBSCRIPTION_SOURCE="$REPO/server/src/com/openrsc/server/content/VoidSubscription.java"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/content/SubscriptionCardTransactionsTest.java"
JDBC_SOURCE="$REPO/server/src/com/openrsc/server/database/JDBCDatabaseConnection.java"
DATABASE_SOURCE="$REPO/server/src/com/openrsc/server/database/GameDatabase.java"
JDBC_DATABASE_SOURCE="$REPO/server/src/com/openrsc/server/database/JDBCDatabase.java"
OUTCOME_SOURCE="$REPO/server/src/com/openrsc/server/database/AtomicTransactionOutcome.java"
COMMERCE_STRUCT_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/PortalCommerceEntitlement.java"
COMMERCE_LEDGER_SOURCE="$REPO/server/src/com/openrsc/server/database/PortalCommerceLedger.java"
JDBC_TEST_SOURCE="$REPO/tests/java/com/openrsc/server/database/JDBCDatabaseConnectionLockTest.java"
OUTCOME_TEST_SOURCE="$REPO/tests/java/com/openrsc/server/database/AtomicTransactionOutcomeTest.java"
COMMERCE_SQLITE_TEST_SOURCE="$REPO/tests/java/com/openrsc/server/database/PortalCommerceLedgerSqliteTest.java"
COMMERCE_SQLITE_SCHEMA="$REPO/server/database/sqlite/patches/2026_07_11_add_portal_commerce.sql"
VENDOR_SOURCE="$REPO/server/plugins/com/openrsc/server/plugins/custom/npcs/VoidSubscriptionVendor.java"
CARD_SOURCE="$REPO/server/plugins/com/openrsc/server/plugins/custom/itemactions/SubscriptionCard.java"

if [ ! -f "$REPO/server/core.jar" ] || [ ! -f "$REPO/server/plugins.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subscription-card-transactions.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" "$OUTCOME_SOURCE" "$COMMERCE_STRUCT_SOURCE" "$COMMERCE_LEDGER_SOURCE" \
	"$DATABASE_SOURCE" "$JDBC_SOURCE" "$JDBC_DATABASE_SOURCE" "$SUBSCRIPTION_SOURCE" "$SOURCE" \
	"$TEST_SOURCE" "$JDBC_TEST_SOURCE" "$OUTCOME_TEST_SOURCE" "$COMMERCE_SQLITE_TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.content.SubscriptionCardTransactionsTest
java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.database.JDBCDatabaseConnectionLockTest
java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.database.AtomicTransactionOutcomeTest
java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.database.PortalCommerceLedgerSqliteTest "$COMMERCE_SQLITE_SCHEMA"

# Production integration assertions: vendor/card plugins must use the coordinator and
# may not reintroduce an asynchronous save or provenance write outside its transaction.
rg -Fq 'SubscriptionCardTransactions.grantReservedCard' "$VENDOR_SOURCE"
rg -Fq 'SubscriptionCardTransactions.claimPurchasedCard' "$VENDOR_SOURCE"
rg -Fq 'EntitlementMarker.global(cacheKey, VoidSubscription.LAUNCH_CARD_AVAILABLE' "$VENDOR_SOURCE"
rg -Fq '"subscription_launch_2026", "campaign=launch_subcard_2026"' "$VENDOR_SOURCE"
rg -Fq 'SubscriptionCardTransactions.redeem' "$CARD_SOURCE"
rg -Fq 'ActionSender.sendGameSettings(player);' "$CARD_SOURCE"
if rg -q 'player\.save|saveReservedAsync|submitSqlLogging|VoidSubscription\.activate' \
	"$VENDOR_SOURCE" "$CARD_SOURCE"; then
	echo "Subscription-card plugins bypass the atomic transaction coordinator" >&2
	exit 1
fi
rg -Fq 'return database.atomicallySettled(body::run, verifier::verify);' "$SOURCE"
rg -Fq 'database.savePlayerInventory(player);' "$SOURCE"
rg -Fq 'database.addItemProvenanceEvent' "$SOURCE"
rg -Fq 'player.tryReserveSave()' "$SOURCE"
rg -Fq 'player.releaseSaveReservation()' "$SOURCE"
rg -Fq 'player.getClientLimitations().maxItemId < VoidSubscription.CARD_ITEM_ID' "$SOURCE"
rg -Fq 'while (items.size() > insertedIndex)' "$SOURCE"
rg -Fq 'queryClaimPortalCommerceEntitlement(entitlement.id, accountId' "$SOURCE"
rg -Fq 'VoidSubscription.CARD_ITEM_ID, claimedAtMs' "$SOURCE"
rg -Fq 'ORDER BY `created_at_ms` ASC, `id` ASC LIMIT 1' "$COMMERCE_LEDGER_SOURCE"
rg -Fq 'AND `claimed_player_id` IS NULL AND `claimed_item_id` IS NULL' "$COMMERCE_LEDGER_SOURCE"
rg -Fq 'return cacheAccountExpiresAt(player, value);' "$SUBSCRIPTION_SOURCE"

echo "Subscription-card production integration checks passed."
