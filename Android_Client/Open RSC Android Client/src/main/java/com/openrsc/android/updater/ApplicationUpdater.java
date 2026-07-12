package com.openrsc.android.updater;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.openrsc.client.R;

public class ApplicationUpdater extends Activity {

	private static final long START_DELAY_MS = 350L;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final Runnable openCacheUpdater = () -> {
		if (isFinishing() || isDestroyed()) {
			return;
		}
		Intent mainIntent = new Intent(ApplicationUpdater.this, CacheUpdater.class);
		Bundle extras = getIntent() == null ? null : getIntent().getExtras();
		if (extras != null) {
			mainIntent.putExtras(extras);
		}
		mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(mainIntent);
		finish();
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!isTaskRoot()) {
			finish();
			return;
		}

		setContentView(R.layout.applicationupdater);

		TextProgressBar progressBar = findViewById(R.id.progressBar1);
		progressBar.setTextSize(18);
		progressBar.setTextColor(getColor(R.color.voidscape_ink));
		progressBar.setIndeterminate(false);
		progressBar.setProgress(100);
		progressBar.setText("Voidscape");

		TextView status = findViewById(R.id.textView1);
		status.setText("Preparing game data");

		mainHandler.postDelayed(openCacheUpdater, START_DELAY_MS);
	}

	@Override
	protected void onDestroy() {
		mainHandler.removeCallbacks(openCacheUpdater);
		super.onDestroy();
	}
}
