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
import android.text.InputType;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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
import java.util.Map;

import orsc.graphics.two.MudClientGraphics;
import orsc.mudclient;
import orsc.multiclient.ClientPort;
import orsc.osConfig;
import orsc.util.Utils;

public abstract class RSCBitmapSurfaceView extends SurfaceView implements SurfaceHolder.Callback, ClientPort {

	private final int client_width = 512;
	private final int client_height = 334;
	private static final int CLIENT_FULL_HEIGHT = 334 + 12;
	private static final int MAX_PORTRAIT_FULL_HEIGHT = 1152;

	protected final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Object frameLock = new Object();
	private Bitmap currentFrame = Bitmap.createBitmap(512, CLIENT_FULL_HEIGHT, Bitmap.Config.RGB_565);

	private final GameActivity gameActivity;
	private boolean m_hb;

	private Map<Integer, Sprite> statusSprites = new HashMap<>();

	public RSCBitmapSurfaceView(Context c) {
		super(c);
		gameActivity = (GameActivity) c;
		Utils.context = c;
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);

		setLongClickable(true);
		setClickable(true);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setKeepScreenOn(true);
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
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		requestClientResize(width, height);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo editorinfo) {
		BaseInputConnection bic = new BaseInputConnection(this, false);
		editorinfo.actionLabel = null;
		editorinfo.inputType = InputType.TYPE_NULL;
		editorinfo.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
		bic.finishComposingText();
		return bic;
	}

	@Override
	public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) { // @NonNull?
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && osConfig.F_SHOWING_KEYBOARD) {
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

			int x = (this.client_width - 281) / 2;
			int y = (this.client_height - 148) / 2;

			Paint paint = new Paint();
			paint.setTextSize(15);
			paint.setTextAlign(Align.CENTER);
			synchronized (frameLock) {
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
					canvas.drawText("Voidscape", x + 138, client_height - 20, paint);
				}
			}
		} catch (Exception var6) {
			var6.printStackTrace();
		}
	}

	@Override
	public void showLoadingProgress(int percentage, String status) {
		drawLoadingScreen(status, percentage);
		int x = (this.client_width - 281) / 2;
		x += 2;
		int y = (this.client_height - 148) / 2;
		y += 90;
		int progress = percentage * 277 / 100;

		synchronized (frameLock) {
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
		int x = (this.client_width - 281) / 2;
		int y = (this.client_height - 148) / 2;
		x += 2;
		y += 90;

		synchronized (frameLock) {
			Canvas canvas = new Canvas(currentFrame);
			Paint paint = new Paint();
			paint.setColor(Color.rgb(198, 198, 198));
			paint.setTextSize(15);
			paint.setTextAlign(Align.CENTER);
			canvas.drawText("Open app again and choose Public.", x + 138, y + 62, paint);
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

			int x = 512 / 2 - 140;
			int y = 334 / 2 - 25;
			paint.setStyle(Paint.Style.FILL);
			canvas.drawRect(x, y, x + 280, y + 50, paint);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.WHITE);
			canvas.drawRect(x, y, x + 280, y + 50, paint);

			paint.setTextAlign(Align.CENTER);
			canvas.drawText(line1, client_width >> 1, (client_height >> 1) - 10, paint);
			canvas.drawText(line2, client_width >> 1, 10 + (client_height >> 1), paint);
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
		int resizedWidth = c.getWidth();
		int resizedHeight = c.getHeight();
		float gameWidth = client.getGameWidth();
		float gameHeight = client.getGameHeight() + 12;
		if (gameWidth <= 0 || gameHeight <= 0) {
			return;
		}
		float scale = Math.min(resizedWidth / gameWidth, resizedHeight / gameHeight);
		float left = (resizedWidth - gameWidth * scale) / 2.0f;
		float top = (resizedHeight - gameHeight * scale) / 2.0f;
		c.translate(left, top);
		c.scale(scale, scale);
		synchronized (frameLock) {
			c.drawBitmap(currentFrame, 0, 0, bitmapPaint);
		}
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

	}

	private void requestClientResize(int surfaceWidth, int surfaceHeight) {
		if (surfaceWidth <= 0 || surfaceHeight <= 0) {
			return;
		}
		int targetWidth = client_width;
		int targetFullHeight = CLIENT_FULL_HEIGHT;
		if (surfaceHeight > surfaceWidth) {
			float aspect = (float) surfaceHeight / (float) surfaceWidth;
			targetFullHeight = clamp(Math.round(targetWidth * aspect), CLIENT_FULL_HEIGHT, MAX_PORTRAIT_FULL_HEIGHT);
		}

		mudclient client = gameActivity.getMudclient();
		if (client != null) {
			int currentFullHeight = client.getGameHeight() + 12;
			if (client.getGameWidth() != targetWidth || currentFullHeight != targetFullHeight) {
				client.resizeWidth = targetWidth;
				client.resizeHeight = targetFullHeight;
			}
		}
		synchronized (frameLock) {
			ensureFrameBitmap(targetWidth, targetFullHeight);
		}
		postInvalidate();
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

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
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
