package com.usama.aianimationstudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 101;
    private final ArrayList<Uri> selected = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private ProgressBar progress;
    private Button renderButton;
    private boolean rendering = false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(28), dp(22), dp(24));
        page.setBackgroundColor(Color.rgb(17, 20, 32));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("AI ANIMATION STUDIO");
        title.setTextSize(25);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        page.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("OFFLINE PHOTO BATCH STUDIO");
        subtitle.setTextColor(Color.rgb(155, 174, 210));
        subtitle.setTextSize(12);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(26));
        page.addView(subtitle, matchWrap());

        TextView info = new TextView(this);
        info.setText("Choose several photos, then render them one by one. Your photos stay on your phone.");
        info.setTextColor(Color.WHITE);
        info.setTextSize(16);
        info.setPadding(dp(14), dp(16), dp(14), dp(16));
        info.setBackgroundColor(Color.rgb(35, 42, 62));
        page.addView(info, matchWrap());

        Button choose = makeButton("1. SELECT MULTIPLE PHOTOS");
        page.addView(choose, buttonParams());
        choose.setOnClickListener(v -> pickImages());

        status = new TextView(this);
        status.setText("No photos selected");
        status.setTextColor(Color.rgb(210, 220, 240));
        status.setTextSize(15);
        status.setPadding(0, dp(16), 0, dp(12));
        page.addView(status, matchWrap());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        page.addView(progress, matchWrap());

        renderButton = makeButton("2. ENHANCE & RENDER ALL");
        page.addView(renderButton, buttonParams());
        renderButton.setOnClickListener(v -> startBatch());

        TextView note = new TextView(this);
        note.setText("\nSTYLE: painterly color blocks, stronger contrast, cool shadows and warm highlights.\n\nIMPORTANT: this first build uses a simple offline image-processing effect. It is not yet a neural-network AI model, so it cannot reproduce the full Arcane look. A compatible offline AI model must be integrated and tested separately.");
        note.setTextColor(Color.rgb(170, 182, 205));
        note.setTextSize(13);
        note.setPadding(0, dp(20), 0, 0);
        page.addView(note, matchWrap());
        setContentView(scroll);
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        selected.clear();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) {}
                selected.add(uri);
            }
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) {}
            selected.add(uri);
        }
        status.setText(selected.size() + " photo(s) selected. Ready to render.");
        progress.setProgress(0);
    }

    private void startBatch() {
        if (rendering) return;
        if (selected.isEmpty()) {
            Toast.makeText(this, "Please select photos first.", Toast.LENGTH_SHORT).show();
            return;
        }
        rendering = true;
        renderButton.setEnabled(false);
        progress.setProgress(0);
        ArrayList<Uri> workList = new ArrayList<>(selected);
        worker.execute(() -> {
            int done = 0, failed = 0;
            for (Uri uri : workList) {
                try {
                    Bitmap source = decode(uri);
                    if (source == null) throw new Exception("Could not decode image");
                    Bitmap result = painterlyEnhance(source);
                    saveImage(result);
                    source.recycle();
                    result.recycle();
                } catch (Exception e) { failed++; }
                done++;
                final int completed = done;
                final int errors = failed;
                runOnUiThread(() -> {
                    progress.setProgress((completed * 100) / workList.size());
                    status.setText("Rendering " + completed + " / " + workList.size()
                            + (errors > 0 ? "  •  " + errors + " failed" : ""));
                });
            }
            final int errors = failed;
            runOnUiThread(() -> {
                rendering = false;
                renderButton.setEnabled(true);
                status.setText("Finished: " + (workList.size() - errors) + " saved"
                        + (errors > 0 ? ", " + errors + " failed" : "")
                        + ". Check Pictures/AI Animation Studio.");
                Toast.makeText(this, "Batch rendering complete.", Toast.LENGTH_LONG).show();
            });
        });
    }

    private Bitmap decode(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1400) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    // Offline illustrative color treatment; not neural style transfer.
    private Bitmap painterlyEnhance(Bitmap input) {
        int w = input.getWidth(), h = input.getHeight();
        int[] pixels = new int[w * h];
        input.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] out = new int[pixels.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, c = pixels[i];
            float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
            float lum = 0.2126f*r + 0.7152f*g + 0.0722f*b;
            r = clamp((r - .5f) * 1.18f + .5f);
            g = clamp((g - .5f) * 1.18f + .5f);
            b = clamp((b - .5f) * 1.18f + .5f);
            float gray = (r + g + b) / 3f;
            r = clamp(gray + (r-gray)*1.22f);
            g = clamp(gray + (g-gray)*1.22f);
            b = clamp(gray + (b-gray)*1.22f);
            if (lum < .42f) { r *= .88f; g *= .94f; b = clamp(b*1.12f + .025f); }
            else { r = clamp(r*1.07f + .025f); g *= 1.015f; b *= .96f; }
            r = posterize(r, 7); g = posterize(g, 7); b = posterize(b, 7);
            if (x > 0 && y > 0 && x < w-1 && y < h-1) {
                float edge = Math.abs(luminance(pixels[i-1])-luminance(pixels[i+1]))
                           + Math.abs(luminance(pixels[i-w])-luminance(pixels[i+w]));
                float ink = Math.max(.78f, 1f-edge*.72f);
                r *= ink; g *= ink; b *= ink;
            }
            out[i] = Color.rgb((int)(clamp(r)*255), (int)(clamp(g)*255), (int)(clamp(b)*255));
        }
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, w, 0, 0, w, h);
        return result;
    }

    private float luminance(int c) {
        return (0.2126f*Color.red(c)+0.7152f*Color.green(c)+0.0722f*Color.blue(c))/255f;
    }
    private float posterize(float v, int levels) { return Math.round(clamp(v)*(levels-1))/(float)(levels-1); }
    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void saveImage(Bitmap bitmap) throws Exception {
        String name = "AI_Animation_" + System.currentTimeMillis() + ".jpg";
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Animation Studio");
        Uri outUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outUri == null) throw new Exception("Could not create output file");
        try (OutputStream out = getContentResolver().openOutputStream(outUri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out))
                throw new Exception("Could not save output");
        }
    }

    private Button makeButton(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); return b;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(18); return p;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdown(); }
}
