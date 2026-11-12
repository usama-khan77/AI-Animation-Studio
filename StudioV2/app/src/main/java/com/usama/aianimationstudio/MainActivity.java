package com.usama.aianimationstudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 40, 28, 28);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("AI Animation Studio");
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("Transform your photos into a cinematic painterly animation style.");
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 20, 0, 25);
        layout.addView(description);

        Button choose = new Button(this);
        choose.setText("Choose Photos");
        layout.addView(choose);

        status = new TextView(this);
        status.setText("No photos selected");
        status.setTextSize(16);
        status.setPadding(0, 25, 0, 25);
        layout.addView(status);

        Button transform = new Button(this);
        transform.setText("Transform Images");
        layout.addView(transform);

        transform.setOnClickListener(v -> {
            if (selectedImages.isEmpty()) {
                Toast.makeText(this, "Choose photos first", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Transformation engine is not connected yet.", Toast.LENGTH_LONG).show();
            }
        });

        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 100);
        });

        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            selectedImages.clear();

            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    selectedImages.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                selectedImages.add(data.getData());
            }

            status.setText(selectedImages.size() + " photo(s) selected");
        }
    }
}
