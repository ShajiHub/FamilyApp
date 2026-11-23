package com.shajitha.familyapp;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MealPlanner extends BaseActivity {

    private static final String STORE_KEY = "meal_today";

    /**
     * Middle of each slice on the wheel image, in degrees clockwise from the top.
     * The slices are not all the same size, so these were measured from foodspinner.png.
     * Same order as R.array.foodmenu.
     */
    private static final float[] SLICE_CENTERS = {353.5f, 39f, 84.4f, 128.5f, 172.7f, 217.9f, 263.5f, 308.6f};
    /** How far from the middle of a slice the wheel may stop, so it still lands inside the slice. */
    private static final float MAX_JITTER = 15f;

    private Spinner spinnerMenu;
    private ImageView spinBoard;
    private Button spin;
    private TextView resultText;
    private String[] meals;
    private final Random random = new Random();
    private ObjectAnimator spinAnimator;
    /** Menu position last shown, so the spinner's automatic first callback is ignored. */
    private int shownPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_planner);

        meals = getResources().getStringArray(R.array.foodmenu);
        spinnerMenu = findViewById(R.id.mealspinner1);
        spin = findViewById(R.id.spin);
        spinBoard = findViewById(R.id.spinBoard);
        resultText = findViewById(R.id.spintext);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.foodmenu, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMenu.setAdapter(adapter);

        // Show today's lunch if one was already chosen.
        int saved = savedMealForToday();
        if (saved >= 0) {
            spinnerMenu.setSelection(saved, false);
            spinBoard.setRotation(-SLICE_CENTERS[saved]);
            showResult(saved);
        }

        shownPosition = spinnerMenu.getSelectedItemPosition();

        // Picking from the menu also sets today's lunch.
        spinnerMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != shownPosition) {
                    shownPosition = position;
                    saveMeal(position);
                    spinBoard.setRotation(-SLICE_CENTERS[position]);
                    showResult(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spin.setOnClickListener(view -> spinWheel());
    }

    private void spinWheel() {
        int winner = random.nextInt(meals.length);
        float jitter = (random.nextFloat() * 2 - 1) * MAX_JITTER;

        // The pointer is at the top: turn the wheel so the winning slice ends up under it,
        // after a few full turns for fun.
        float current = spinBoard.getRotation() % 360;
        spinBoard.setRotation(current);
        float target = -(SLICE_CENTERS[winner] + jitter);
        float delta = ((target - current) % 360 + 360) % 360;
        int fullTurns = 4 + random.nextInt(3);
        float end = current + fullTurns * 360 + delta;

        spin.setEnabled(false);
        spinnerMenu.setEnabled(false);
        resultText.setText(R.string.meal_spinning);

        spinAnimator = ObjectAnimator.ofFloat(spinBoard, View.ROTATION, current, end);
        spinAnimator.setDuration(3000);
        spinAnimator.setInterpolator(new DecelerateInterpolator(2f));
        spinAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spin.setEnabled(true);
                spinnerMenu.setEnabled(true);
                saveMeal(winner);
                shownPosition = winner;
                spinnerMenu.setSelection(winner);
                showResult(winner);
            }
        });
        spinAnimator.start();
    }

    private void showResult(int index) {
        resultText.setText(getString(R.string.meal_result, meals[index]));
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void saveMeal(int index) {
        FamilyStore.putString(this, STORE_KEY, today() + "|" + meals[index]);
    }

    /** Index of the meal chosen today, or -1 if none was chosen yet. */
    private int savedMealForToday() {
        String saved = FamilyStore.getString(this, STORE_KEY);
        if (saved == null || !saved.startsWith(today() + "|")) {
            return -1;
        }
        String meal = saved.substring(saved.indexOf('|') + 1);
        for (int i = 0; i < meals.length; i++) {
            if (meals[i].equals(meal)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spinAnimator != null) {
            spinAnimator.removeAllListeners();
            spinAnimator.cancel();
        }
    }
}
