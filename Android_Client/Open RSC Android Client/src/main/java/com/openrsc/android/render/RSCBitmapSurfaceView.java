package com.openrsc.android.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;

import com.openrsc.client.R;
import com.openrsc.client.android.GameActivity;
import com.openrsc.client.model.Sprite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import orsc.graphics.two.MudClientGraphics;
import orsc.mudclient;
import orsc.multiclient.ClientPort;
import orsc.osConfig;

public abstract class RSCBitmapSurfaceView extends SurfaceView implements SurfaceHolder.Callback, ClientPort {

	protected final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Object frameLock = new Object();
	private final Object viewportLock = new Object();
	private Bitmap currentFrame = Bitmap.createBitmap(AndroidClientViewport.BASE_WIDTH, AndroidClientViewport.BASE_FULL_HEIGHT, Bitmap.Config.RGB_565);
	private volatile AndroidClientViewport.Metrics viewportMetrics;
	private AndroidClientViewport.SafeInsets safeInsets = AndroidClientViewport.SafeInsets.NONE;
	private volatile int imeBottomInset;
	private volatile boolean imeVisible;
	private int viewportSurfaceWidth;
	private int viewportSurfaceHeight;
	private String lastViewportLogLine;

	private final GameActivity gameActivity;
	private boolean m_hb;

	private Map<Integer, Sprite> statusSprites = new HashMap<>();

	public RSCBitmapSurfaceView(Context c) {
		super(c);
		gameActivity = (GameActivity) c;
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);

		setLongClickable(true);
		setClickable(true);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setKeepScreenOn(true);
		setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
			@Override
			public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
				AndroidClientViewport.SafeInsets nextSafeInsets = readSafeInsets(windowInsets);
				updateSafeInsets(nextSafeInsets);
				int nextImeBottomInset = readImeBottomInset(windowInsets, nextSafeInsets.bottom);
				updateImeState(
					nextImeBottomInset,
					readImeVisible(windowInsets, nextImeBottomInset)
				);
				return windowInsets;
			}
		});
		loadStatusSprites();
	}

	private void loadStatusSprites() {
		int[] drawableIds = {R.drawable.battery_empty, R.drawable.battery_1_bar, R.drawable.battery_2_bar, R.drawable.battery_3_bar,
			R.drawable.battery_4_bar, R.drawable.battery_5_bar, R.drawable.battery_6_bar, R.drawable.battery_full, R.drawable.battery_charging,
			R.drawable.network_none, R.drawable.network_cell, R.drawable.network_wifi};
		for (int drawableId : drawableIds) {
			statusSprites.put(drawableId, getSpriteFromDrawableId(drawableId));
		}
	}

	@Override
	public void surfaceCreated(final SurfaceHolder holder) {
		setWillNotDraw(false);
		requestApplyInsets();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		updateViewportMetrics(width, height);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo editorinfo) {
		editorinfo.actionLabel = null;
		editorinfo.inputType = InputType.TYPE_CLASS_TEXT
			| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
			| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
		editorinfo.imeOptions = EditorInfo.IME_ACTION_DONE
			| EditorInfo.IME_FLAG_NO_FULLSCREEN
			| EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		editorinfo.initialSelStart = 0;
		editorinfo.initialSelEnd = 0;

		// fullEditor=false keeps BaseInputConnection in fallback mode. Committed
		// text, backspace, and the IME action are translated into the KeyEvents
		// consumed by InputImpl, while modern IMEs still see a real text editor.
		return new BaseInputConnection(this, false);
	}

	@Override
	public boolean onCheckIsTextEditor() {
		return true;
	}

	@Override
	public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) { // @NonNull?
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && gameActivity.isKeyboardShowing()) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				gameActivity.closeKeyboard();
			}
			return true;
		}
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) { // ACTION_DOWN
			post(new Runnable() {
				@Override
				public void run() {
					gameActivity.onBackPressed();
				}
			});
			return true;
		}
		return super.onKeyPreIme(keyCode, event);
	}

	@Override
	public boolean drawLoading(int i) {
		drawLoadingScreen("Loading...", 0);
		return true;
	}

	private void drawLoadingScreen(String state, int percent) {
		try {
			Paint paint = new Paint();
			paint.setTextSize(15);
			paint.setTextAlign(Align.CENTER);
			synchronized (frameLock) {
				int contentWidth = currentFrame.getWidth();
				int contentHeight = getFrameContentHeight();
				int x = (contentWidth - 281) / 2;
				int y = (contentHeight - 148) / 2;
				Canvas canvas = new Canvas(currentFrame);
				canvas.drawColor(0, Mode.CLEAR);

				paint.setStyle(Paint.Style.FILL);
				canvas.drawRect(0, 0, currentFrame.getWidth(), currentFrame.getHeight(), paint);
				paint.setStyle(Paint.Style.STROKE);

				// if (!this.m_hb) {
				// canvas.drawBitmap(this.loadingJagLogo, (float) x, (float) y,
				// null);
				// }

				x += 2;
				y += 90;

				paint.setColor(Color.rgb(132, 132, 132));
				if (this.m_hb) {
					paint.setColor(Color.rgb(220, 0, 0));
				}

				paint.setStyle(Paint.Style.STROKE);
				canvas.drawRect(x - 2, y - 2, x + 280, y + 23, paint);

				paint.setStyle(Paint.Style.FILL);
				canvas.drawRect(x, y, x + ((percent * 277) / 100), y + 20, paint);

				paint.setStyle(Paint.Style.STROKE);

				paint.setColor(Color.rgb(198, 198, 198));
				if (this.m_hb) {
					paint.setColor(Color.rgb(255, 255, 255));
				}

				canvas.drawText(state, x + 138, y + 10, paint);

				if (!this.m_hb) {
					canvas.drawText("Voidscape", x + 138, y + 30, paint);
					canvas.drawText("Classic adventure, rebuilt", x + 138, y + 44, paint);
				} else {
					paint.setColor(Color.rgb(132, 132, 152));
					canvas.drawText("Voidscape", x + 138, contentHeight - 20, paint);
				}
			}
		} catch (Exception var6) {
			var6.printStackTrace();
		}
	}

	@Override
	public void showLoadingProgress(int percentage, String status) {
		drawLoadingScreen(status, percentage);
		int progress = percentage * 277 / 100;

		synchronized (frameLock) {
			int x = (currentFrame.getWidth() - 281) / 2;
			x += 2;
			int y = (getFrameContentHeight() - 148) / 2;
			y += 90;
			Canvas canvas = new Canvas(currentFrame);
			Paint paint = new Paint();
			paint.setColor(Color.rgb(132, 132, 132));
			if (this.m_hb) {
				paint.setColor(Color.rgb(220, 0, 0));
			}

			paint.setStyle(Paint.Style.FILL);
			canvas.drawRect(x, y, x + progress, y + 20, paint);

			paint.setColor(Color.BLACK);

			canvas.drawRect(progress + x, y, x + 277 - progress, y + 20, paint);

			paint.setColor(Color.rgb(198, 198, 198));
			if (this.m_hb) {
				paint.setColor(Color.rgb(255, 255, 255));
			}

			paint.setTextAlign(Align.CENTER);
			canvas.drawText(status, x + 138, y + 10, paint);
		}
	}

	@Override
	public void initListeners() {

	}

	@Override
	public void crashed() {

	}

	@Override
	public void drawLoadingError() {
		drawLoadingScreen("Can't reach selected server", 0);

		synchronized (frameLock) {
			int x = (currentFrame.getWidth() - 281) / 2;
			int y = (getFrameContentHeight() - 148) / 2;
			x += 2;
			y += 90;
			Canvas canvas = new Canvas(currentFrame);
			Paint paint = new Paint();
			paint.setColor(Color.rgb(198, 198, 198));
			paint.setTextSize(15);
			paint.setTextAlign(Align.CENTER);
			canvas.drawText("Check connection and reopen app.", x + 138, y + 62, paint);
		}
	}

	@Override
	public void drawOutOfMemoryError() {
		drawLoadingScreen("Out of memory", 0);
	}

	@Override
	public boolean isDisplayable() {
		return true;
	}

	@Override
	public void drawTextBox(String line2, byte var2, String line1) {
		synchronized (frameLock) {
			Canvas canvas = new Canvas(currentFrame);

			Paint paint = new Paint();

			paint.setColor(Color.rgb(132, 132, 132));
			if (this.m_hb) {
				paint.setColor(Color.rgb(220, 0, 0));
			}

				int centerX = currentFrame.getWidth() >> 1;
				int centerY = getFrameContentHeight() >> 1;
				int x = centerX - 140;
				int y = centerY - 25;
			paint.setStyle(Paint.Style.FILL);
			canvas.drawRect(x, y, x + 280, y + 50, paint);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.WHITE);
				canvas.drawRect(x, y, x + 280, y + 50, paint);

				paint.setTextAlign(Align.CENTER);
				canvas.drawText(line1, centerX, centerY - 10, paint);
				canvas.drawText(line2, centerX, centerY + 10, paint);
				paint.setColor(Color.BLACK);
		}
	}

	@Override
	public void initGraphics() {
	}

	@Override
	public void draw() {
		mudclient client = gameActivity.getMudclient();
		if (client == null) {
			return;
		}

		MudClientGraphics surface = client.getSurface();
		if (surface == null || surface.pixelData == null) {
			return;
		}

		int gameWidth = client.getGameWidth();
		int gameHeight = client.getGameHeight() + 12;
		if (gameWidth <= 0 || gameHeight <= 0) {
			return;
		}

		synchronized (frameLock) {
			ensureFrameBitmap(gameWidth, gameHeight);
			int copyWidth = Math.min(gameWidth, currentFrame.getWidth());
			int copyHeight = Math.min(gameHeight, currentFrame.getHeight());
			int requiredPixels = ((copyHeight - 1) * gameWidth) + copyWidth;
			if (copyWidth <= 0 || copyHeight <= 0 || surface.pixelData.length < requiredPixels) {
				return;
			}

			currentFrame.setPixels(surface.pixelData, 0, gameWidth, 0, 0, copyWidth, copyHeight);
		}
		postInvalidate();
	}

	private void doDraw(Canvas c) {
		mudclient client = gameActivity.getMudclient();
		if (client == null) {
			return;
		}
		c.drawRGB(0, 0, 0);
		int gameWidth = client.getGameWidth();
		int gameHeight = client.getGameHeight() + 12;
		if (gameWidth <= 0 || gameHeight <= 0) {
			return;
		}
		AndroidClientViewport.Transform transform = getClientTransform(gameWidth, gameHeight);
		int saveCount = c.save();
		c.translate(transform.offsetX, transform.offsetY);
		c.scale(transform.scale, transform.scale);
		synchronized (frameLock) {
			c.clipRect(0, 0, gameWidth, gameHeight);
			c.drawBitmap(currentFrame, 0, 0, bitmapPaint);
		}
		c.restoreToCount(saveCount);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		doDraw(canvas);
	}

	@Override
	public void close() {

	}

	@Override
	public String getCacheLocation() {
		return getContext().getFilesDir().getAbsolutePath() + File.separator;
	}

	@Override
	public void resized() {
		emitViewportLogIfChanged();
	}

	public void refreshViewportMetrics() {
		requestApplyInsets();
		int width = getWidth();
		int height = getHeight();
		if (width > 0 && height > 0) {
			updateViewportMetrics(width, height);
		}
	}

	private void updateViewportMetrics(int surfaceWidth, int surfaceHeight) {
		if (surfaceWidth <= 0 || surfaceHeight <= 0) {
			return;
		}
		AndroidClientViewport.Metrics metrics;
		synchronized (viewportLock) {
			viewportSurfaceWidth = surfaceWidth;
			viewportSurfaceHeight = surfaceHeight;
			metrics = AndroidClientViewport.metricsForSurface(
				surfaceWidth,
				surfaceHeight,
				safeInsets,
				getResources().getDisplayMetrics().density
			);
			viewportMetrics = metrics;
		}
		applyClientTarget(metrics);
		emitViewportLogIfChanged();
		postInvalidate();
	}

	private void updateSafeInsets(AndroidClientViewport.SafeInsets nextInsets) {
		int width;
		int height;
		synchronized (viewportLock) {
			if (safeInsets.sameAs(nextInsets)) {
				return;
			}
			safeInsets = nextInsets;
			width = viewportSurfaceWidth > 0 ? viewportSurfaceWidth : getWidth();
			height = viewportSurfaceHeight > 0 ? viewportSurfaceHeight : getHeight();
		}
		if (width > 0 && height > 0) {
			updateViewportMetrics(width, height);
		}
	}

	private void applyClientTarget(AndroidClientViewport.Metrics metrics) {
		AndroidClientViewport.TargetSize target = metrics.target;

		mudclient client = gameActivity.getMudclient();
		// Configuration changes briefly leave the old SurfaceView alive beside the
		// replacement Activity. Only the Activity currently bound as ClientPort may
		// resize the retained client; otherwise a late portrait callback can overwrite
		// the replacement's settled landscape target (and vice versa).
		if (client != null && mudclient.clientPort == gameActivity) {
			int currentFullHeight = client.getGameHeight() + 12;
			if (client.getGameWidth() != target.width || currentFullHeight != target.fullHeight) {
				if (client.resizeWidth != target.width || client.resizeHeight != target.fullHeight) {
					client.resizeWidth = target.width;
					client.resizeHeight = target.fullHeight;
				}
			}
		}
		synchronized (frameLock) {
			ensureFrameBitmap(target.width, target.fullHeight);
		}
	}

	AndroidClientViewport.Transform getClientTransform(int logicalWidth, int logicalFullHeight) {
		AndroidClientViewport.Metrics metrics = getViewportMetrics();
		return metrics.transformFor(logicalWidth, logicalFullHeight);
	}

	@Override
	public int getTouchTargetClientPixels(int dp) {
		AndroidClientViewport.Metrics metrics = getViewportMetrics();
		mudclient client = gameActivity.getMudclient();
		int logicalWidth = client == null ? metrics.target.width : Math.max(1, client.getGameWidth());
		int logicalFullHeight = client == null
			? metrics.target.fullHeight
			: Math.max(1, client.getGameHeight() + 12);
		return AndroidClientViewport.logicalPixelsForDp(
			dp,
			metrics.density,
			metrics.transformFor(logicalWidth, logicalFullHeight)
		);
	}

	@Override
	public int getKeyboardTopClientPixel() {
		int bottomInset = imeBottomInset;
		if (bottomInset <= 0) {
			return Integer.MAX_VALUE;
		}
		AndroidClientViewport.Metrics metrics = getViewportMetrics();
		mudclient client = gameActivity.getMudclient();
		int logicalWidth = client == null ? metrics.target.width : Math.max(1, client.getGameWidth());
		int logicalFullHeight = client == null
			? metrics.target.fullHeight
			: Math.max(1, client.getGameHeight() + 12);
		AndroidClientViewport.Transform transform = metrics.transformFor(logicalWidth, logicalFullHeight);
		float keyboardTop = metrics.surfaceHeight - bottomInset;
		int logicalTop = (int) Math.floor((keyboardTop - transform.offsetY) / transform.scale);
		int gameHeight = client == null ? Math.max(1, logicalFullHeight - 12)
			: Math.max(1, client.getGameHeight());
		return Math.max(0, Math.min(gameHeight, logicalTop));
	}

	private void updateImeState(int bottomInset, boolean visible) {
		int sanitized = Math.max(0, Math.min(Math.max(1, getHeight()), bottomInset));
		boolean insetChanged = imeBottomInset != sanitized;
		boolean visibilityChanged = imeVisible != visible;
		if (!insetChanged && !visibilityChanged) {
			return;
		}
		imeBottomInset = sanitized;
		imeVisible = visible;
		if (visibilityChanged) {
			gameActivity.onImeVisibilityChanged(visible);
		}
		emitViewportLogIfChanged();
		postInvalidate();
	}

	private AndroidClientViewport.Metrics getViewportMetrics() {
		AndroidClientViewport.Metrics metrics = viewportMetrics;
		if (metrics != null) {
			return metrics;
		}
		int width = Math.max(1, getWidth());
		int height = Math.max(1, getHeight());
		return AndroidClientViewport.metricsForSurface(
			width,
			height,
			safeInsets,
			getResources().getDisplayMetrics().density
		);
	}

	private void emitViewportLogIfChanged() {
		AndroidClientViewport.Metrics metrics = getViewportMetrics();
		mudclient client = gameActivity.getMudclient();
		int logicalWidth = client == null ? metrics.target.width : Math.max(1, client.getGameWidth());
		int logicalFullHeight = client == null
			? metrics.target.fullHeight
			: Math.max(1, client.getGameHeight() + 12);
		AndroidClientViewport.Transform transform = metrics.transformFor(logicalWidth, logicalFullHeight);
		int touch44 = AndroidClientViewport.logicalPixelsForDp(44, metrics.density, transform);
		int touch48 = AndroidClientViewport.logicalPixelsForDp(48, metrics.density, transform);
		int keyboardTop = getKeyboardTopClientPixel();
		String line = String.format(
			Locale.US,
			"ANDROID_MOBILE_VIEWPORT surfaceW=%d surfaceH=%d contentW=%d contentH=%d insetL=%d insetT=%d insetR=%d insetB=%d logicalW=%d logicalH=%d scale=%.4f density=%.3f touch44=%d touch48=%d imeBottom=%d keyboardTop=%d",
			metrics.surfaceWidth,
			metrics.surfaceHeight,
			metrics.contentWidth,
			metrics.contentHeight,
			metrics.insets.left,
			metrics.insets.top,
			metrics.insets.right,
			metrics.insets.bottom,
			logicalWidth,
			logicalFullHeight,
			transform.scale,
			metrics.density,
			touch44,
			touch48,
			imeBottomInset,
			keyboardTop
		);
		synchronized (viewportLock) {
			if (line.equals(lastViewportLogLine)) {
				return;
			}
			lastViewportLogLine = line;
		}
		Log.i("Voidscape", line);
	}

	@SuppressWarnings("deprecation")
	private AndroidClientViewport.SafeInsets readSafeInsets(WindowInsets windowInsets) {
		if (windowInsets == null) {
			return AndroidClientViewport.SafeInsets.NONE;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return readSafeInsetsApi30(windowInsets);
		}

		int left = Math.max(0, windowInsets.getStableInsetLeft());
		int top = 0;
		int right = Math.max(0, windowInsets.getStableInsetRight());
		int bottom = Math.max(0, windowInsets.getStableInsetBottom());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			android.graphics.Insets gestures = windowInsets.getMandatorySystemGestureInsets();
			left = Math.max(left, gestures.left);
			top = Math.max(top, gestures.top);
			right = Math.max(right, gestures.right);
			bottom = Math.max(bottom, gestures.bottom);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && windowInsets.getDisplayCutout() != null) {
			left = Math.max(left, windowInsets.getDisplayCutout().getSafeInsetLeft());
			top = Math.max(top, windowInsets.getDisplayCutout().getSafeInsetTop());
			right = Math.max(right, windowInsets.getDisplayCutout().getSafeInsetRight());
			bottom = Math.max(bottom, windowInsets.getDisplayCutout().getSafeInsetBottom());
		}
		return new AndroidClientViewport.SafeInsets(left, top, right, bottom);
	}

	private AndroidClientViewport.SafeInsets readSafeInsetsApi30(WindowInsets windowInsets) {
		int mask = WindowInsets.Type.navigationBars()
			| WindowInsets.Type.displayCutout()
			| WindowInsets.Type.mandatorySystemGestures();
		android.graphics.Insets insets = windowInsets.getInsetsIgnoringVisibility(mask);
		return new AndroidClientViewport.SafeInsets(insets.left, insets.top, insets.right, insets.bottom);
	}

	@SuppressWarnings("deprecation")
	private int readImeBottomInset(WindowInsets windowInsets, int safeBottomInset) {
		if (windowInsets == null) {
			return 0;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return windowInsets.isVisible(WindowInsets.Type.ime())
				? windowInsets.getInsets(WindowInsets.Type.ime()).bottom : 0;
		}
		return Math.max(0, windowInsets.getSystemWindowInsetBottom() - safeBottomInset);
	}

	private boolean readImeVisible(WindowInsets windowInsets, int bottomInset) {
		if (windowInsets == null) {
			return false;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return windowInsets.isVisible(WindowInsets.Type.ime());
		}
		return bottomInset > 0;
	}

	private void ensureFrameBitmap(int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		if (currentFrame != null && currentFrame.getWidth() == width && currentFrame.getHeight() == height) {
			return;
		}
		currentFrame = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
	}

	private int getFrameContentHeight() {
		return Math.max(1, currentFrame.getHeight() - 12);
	}

	@Override
	public Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream) {
		Bitmap bp = BitmapFactory.decodeStream(byteArrayInputStream);
		if (bp == null) {
			return null;
		}
		int width = bp.getWidth();
		int height = bp.getHeight();
		int[] captchaPixels = new int[width * height];
		int px = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				captchaPixels[px++] = bp.getPixel(x, y);
			}
		}

		bp.recycle();
		Sprite sprite = new Sprite(captchaPixels, width, height);
		sprite.setSomething(width, height);
		return sprite;
	}

	private Sprite getSpriteFromDrawableId(int drawableId) {
		Drawable drawable = getResources().getDrawable(drawableId, getContext().getTheme());
		BitmapDrawable bitmapDrawable = ((BitmapDrawable) drawable);
		Bitmap bitmap = bitmapDrawable.getBitmap();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); //use the compression format of your need
		ByteArrayInputStream is = new ByteArrayInputStream(stream.toByteArray());

		return getSpriteFromByteArray(is);
	}

	@Override
	public boolean getBatteryCharging() {
		return gameActivity.getBatteryCharging();
	}

	@Override
	public int getBatteryPercent() {
		return gameActivity.getBatteryPercent();
	}

	@Override
	public Sprite getBattery(int level) {
		int drawableId = R.drawable.battery_empty;
		switch (level) {
			case 8:
				drawableId = R.drawable.battery_charging;
				break;
			case 7:
				drawableId = R.drawable.battery_full;
				break;
			case 6:
				drawableId = R.drawable.battery_6_bar;
				break;
			case 5:
				drawableId = R.drawable.battery_5_bar;
				break;
			case 4:
				drawableId = R.drawable.battery_4_bar;
				break;
			case 3:
				drawableId = R.drawable.battery_3_bar;
				break;
			case 2:
				drawableId = R.drawable.battery_2_bar;
				break;
			case 1:
				drawableId = R.drawable.battery_1_bar;
				break;
			case 0:
				drawableId = R.drawable.battery_empty;
				break;
		}
		return statusSprites.get(drawableId);
	}

	@Override
	public String getConnectivityText() {
		return gameActivity.getConnectivityText();
	}

	@Override
	public Sprite getConnectivity(int level) {
		int drawableId = R.drawable.network_none;
		switch (level) {
			case 2:
				drawableId = R.drawable.network_wifi;
				break;
			case 1:
				drawableId = R.drawable.network_cell;
				break;
			case 0:
				drawableId = R.drawable.network_none;
				break;
		}
		return statusSprites.get(drawableId);
	}

	private AudioTrack audioTrack;

	@Override
	public void playSound(byte[] soundData, int offset, int dataLength) {
		int bufferSize = AudioTrack.getMinBufferSize(16000,
			AudioFormat.CHANNEL_IN_STEREO,
			AudioFormat.ENCODING_PCM_16BIT);
	}

	@Override
	public void stopSoundPlayer() {
	}

	public void drawKeyboard() {
	}

	public void closeKeyboard() {
	}

	@Override
	public boolean openUrl(String url) {
		return gameActivity.openUrl(url);
	}

	public boolean saveCredentials(String creds) {
		return false;
	}

	public String loadCredentials() {
		return null;
	}
}
