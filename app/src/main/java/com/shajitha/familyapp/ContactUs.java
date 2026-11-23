package com.shajitha.familyapp;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

public class ContactUs extends BaseActivity {

    private static final long SLIDE_INTERVAL_MS = 1500;

    private final int[] imgArray1 = {R.drawable.a1, R.drawable.a2, R.drawable.a3, R.drawable.a4, R.drawable.a5,
            R.drawable.a6, R.drawable.a7, R.drawable.a8, R.drawable.a9, R.drawable.a10};
    private final int[] imgArray2 = {R.drawable.b1, R.drawable.b2, R.drawable.b3};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView img1, img2;
    private int i = 0;
    private int j = 0;

    private final Runnable slideshow = new Runnable() {
        @Override
        public void run() {
            img1.setImageResource(imgArray1[i]);
            i = (i + 1) % imgArray1.length;
            img2.setImageResource(imgArray2[j]);
            j = (j + 1) % imgArray2.length;
            handler.postDelayed(this, SLIDE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_us);
        img1 = findViewById(R.id.img1);
        img2 = findViewById(R.id.img2);
    }

    // The slideshow only runs while the screen is visible, and stops when you leave it.
    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(slideshow, 500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(slideshow);
    }
}
