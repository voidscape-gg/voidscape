package orsc.multiclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A complete, immutable credential-store transaction. */
public final class CredentialSnapshot {

	private static final CredentialSnapshot EMPTY = new CredentialSnapshot(null,
		Collections.<SavedCredentialAccount>emptyList(), Collections.<String, String>emptyMap());

	private final String selectedUsername;
	private final List<SavedCredentialAccount> accounts;
	private final Map<String, String> metadata;

	public CredentialSnapshot(String selectedUsername, List<SavedCredentialAccount> accounts) {
		this(selectedUsername, accounts, Collections.<String, String>emptyMap());
	}

	public CredentialSnapshot(String selectedUsername, List<SavedCredentialAccount> accounts,
			Map<String, String> metadata) {
		this.selectedUsername = selectedUsername;
		this.accounts = Collections.unmodifiableList(new ArrayList<>(
			Objects.requireNonNull(accounts, "accounts")));
		this.metadata = immutableCopy(metadata);
	}

	public static CredentialSnapshot empty() {
		return EMPTY;
	}

	public String getSelectedUsername() {
		return selectedUsername;
	}

	public List<SavedCredentialAccount> getAccounts() {
		return accounts;
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public boolean isEmpty() {
		return accounts.isEmpty();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof CredentialSnapshot)) {
			return false;
		}
		CredentialSnapshot snapshot = (CredentialSnapshot) other;
		return Objects.equals(selectedUsername, snapshot.selectedUsername)
			&& accounts.equals(snapshot.accounts)
			&& metadata.equals(snapshot.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(selectedUsername, accounts, metadata);
	}

	@Override
	public String toString() {
		return "CredentialSnapshot{redacted}";
	}

	private static Map<String, String> immutableCopy(Map<String, String> source) {
		Objects.requireNonNull(source, "metadata");
		LinkedHashMap<String, String> copy = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : source.entrySet()) {
			copy.put(Objects.requireNonNull(entry.getKey(), "metadata key"),
				Objects.requireNonNull(entry.getValue(), "metadata value"));
		}
		return Collections.unmodifiableMap(copy);
	}
}
