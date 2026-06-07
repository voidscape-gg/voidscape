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

		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			Intent mainIntent = new Intent(ApplicationUpdater.this, CacheUpdater.class);
			mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(mainIntent);
			finish();
		}, START_DELAY_MS);
	}
}
