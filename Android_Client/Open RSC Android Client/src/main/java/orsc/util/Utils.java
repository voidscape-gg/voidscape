package orsc.util;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

public class Utils {
	private static WeakReference<Activity> activityReference = new WeakReference<>(null);

	public static synchronized void attach(Activity activity) {
		activityReference = new WeakReference<>(activity);
	}

	public static synchronized void detach(Activity activity) {
		if (activityReference.get() == activity) {
			activityReference.clear();
		}
	}

	private static synchronized Activity currentActivity() {
		return activityReference.get();
	}

	private static boolean canShowDialog(Activity activity) {
		return activity != null && !activity.isFinishing() && !activity.isDestroyed();
	}

	public static void openWebpage(final String url) {
		if (url == null || url.trim().isEmpty()) {
			return;
		}
		final String target = url.trim();
		final Handler handlerTimer = new Handler(Looper.getMainLooper());
		handlerTimer.postDelayed(new Runnable() {
			public void run() {
				Activity activity = currentActivity();
				if (!canShowDialog(activity)) {
					return;
				}
				try {
					AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
					alertDialogBuilder
						.setMessage("You are opening an external site. Return to Voidscape when you're done.")
						.setCancelable(true).setPositiveButton("Open", (dialog, id) -> {
							Activity current = currentActivity();
							if (!canShowDialog(current)) {
								return;
							}
							try {
								Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
								current.startActivity(browserIntent);
							} catch (RuntimeException error) {
								error.printStackTrace();
							}
						}).setNegativeButton("Cancel", null);
					alertDialogBuilder.show();
				} catch (RuntimeException error) {
					error.printStackTrace();
				}
			}
		}, 100);
	}
}
