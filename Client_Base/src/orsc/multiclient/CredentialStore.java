package orsc.multiclient;

/**
 * Platform credential persistence. Non-Android clients use the unsupported implementation until
 * they opt into an equivalent secure store.
 */
public interface CredentialStore {

	enum State {
		ABSENT,
		VALUE,
		UNAVAILABLE
	}

	final class Result {
		private static final Result ABSENT = new Result(State.ABSENT, null);
		private static final Result UNAVAILABLE = new Result(State.UNAVAILABLE, null);

		private final State state;
		private final CredentialSnapshot value;

		private Result(State state, CredentialSnapshot value) {
			this.state = state;
			this.value = value;
		}

		public static Result absent() {
			return ABSENT;
		}

		public static Result value(CredentialSnapshot value) {
			if (value == null || value.isEmpty()) {
				throw new IllegalArgumentException("VALUE requires at least one saved account");
			}
			return new Result(State.VALUE, value);
		}

		public static Result unavailable() {
			return UNAVAILABLE;
		}

		public State getState() {
			return state;
		}

		public CredentialSnapshot getValue() {
			return value;
		}

		public boolean hasValue() {
			return state == State.VALUE;
		}
	}

	Result load();

	/** Replaces selected account, accounts, and metadata in one verified transaction. */
	Result replace(CredentialSnapshot snapshot);

	/** Forgets one account using the same case-insensitive username matching as the client. */
	Result forget(String username);

	/** Persists an authoritative empty state so deleted plaintext credentials cannot reappear. */
	Result clear();

	static CredentialStore unsupported() {
		return UnsupportedHolder.INSTANCE;
	}

	final class UnsupportedHolder {
		private static final CredentialStore INSTANCE = new CredentialStore() {
			@Override
			public Result load() {
				return Result.unavailable();
			}

			@Override
			public Result replace(CredentialSnapshot snapshot) {
				return Result.unavailable();
			}

			@Override
			public Result forget(String username) {
				return Result.unavailable();
			}

			@Override
			public Result clear() {
				return Result.unavailable();
			}
		};

		private UnsupportedHolder() {
		}
	}
}
