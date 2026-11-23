package com.shajitha.familyapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.DataSetup;
import com.shajitha.familyapp.data.FamilyDao;
import com.shajitha.familyapp.data.Food;
import com.shajitha.familyapp.data.MealSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Meal Planner: spin the wheel or pick today's lunch, and manage the family's food menu. */
public class MealPlanner extends BaseActivity {

    static final int MIN_FOODS = 2;
    static final int MAX_FOODS = 16;
    private static final long SPIN_MS = 3000;

    private FamilyDao dao;
    private FoodWheelView wheel;
    private Button spin;
    private TextView resultText, menuTitle;
    private MaterialAutoCompleteTextView menuChoice;
    private LinearLayout foodList;
    private final Random random = new Random();
    private ObjectAnimator spinAnimator;
    private List<Food> foods = new ArrayList<>();
    @Nullable
    private String todaysMeal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_planner);
        dao = AppDatabase.get(this).dao();

        wheel = findViewById(R.id.spinBoard);
        spin = findViewById(R.id.spin);
        resultText = findViewById(R.id.spintext);
        menuTitle = findViewById(R.id.menutxt);
        menuChoice = findViewById(R.id.mealspinner1);
        foodList = findViewById(R.id.foodList);

        spin.setOnClickListener(v -> spinWheel());
        findViewById(R.id.addFood).setOnClickListener(v -> addFood());
        // Picking from the menu also sets today's lunch.
        menuChoice.setOnItemClickListener((parent, view, position, id) -> {
            if (position < foods.size()) {
                chooseMeal(foods.get(position).name);
                wheel.showFood(position);
            }
        });

        dao.foods().observe(this, this::showFoods);
        dao.mealOn(DataSetup.today()).observe(this, selection -> {
            todaysMeal = selection == null ? null : selection.food;
            if (!isSpinning()) {
                showResult();
            }
        });
    }

    private boolean isSpinning() {
        return spinAnimator != null && spinAnimator.isRunning();
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (Food f : foods) {
            names.add(f.name);
        }
        return names;
    }

    private void showFoods(List<Food> list) {
        foods = list;
        List<String> names = names();
        wheel.setFoods(names);
        menuChoice.setSimpleItems(names.toArray(new String[0]));
        menuTitle.setText(getResources().getQuantityString(R.plurals.meal_menu_title, names.size(), names.size()));
        spin.setEnabled(names.size() >= MIN_FOODS && !isSpinning());
        findViewById(R.id.addFood).setEnabled(names.size() < MAX_FOODS);

        foodList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Food food : list) {
            View row = inflater.inflate(R.layout.item_list_row, foodList, false);
            row.findViewById(R.id.rowCheck).setVisibility(View.GONE);
            ((TextView) row.findViewById(R.id.rowTitle)).setText(food.name);
            row.findViewById(R.id.rowEdit).setOnClickListener(v -> renameFood(food));
            row.findViewById(R.id.rowDelete).setOnClickListener(v -> deleteFood(food));
            foodList.addView(row);
        }
        if (!isSpinning()) {
            showResult();
        }
    }

    /** Shows today's lunch, and turns the wheel to it when it's on the menu. */
    private void showResult() {
        if (foods.size() < MIN_FOODS) {
            resultText.setText(R.string.meal_need_more);
        } else if (todaysMeal == null) {
            resultText.setText(R.string.meal_prompt);
        } else {
            resultText.setText(getString(R.string.meal_result, todaysMeal));
            int index = names().indexOf(todaysMeal);
            if (index >= 0 && wheel.indexUnderPointer() != index) {
                wheel.showFood(index);
            }
            menuChoice.setText(todaysMeal, false);
        }
    }

    private void spinWheel() {
        if (foods.size() < MIN_FOODS || isSpinning()) {
            return;
        }
        int winner = random.nextInt(foods.size());
        String meal = foods.get(winner).name;
        // Stop somewhere inside the slice, not always in the middle.
        float offset = (random.nextFloat() - 0.5f) * 0.7f;

        float current = wheel.getWheelRotation() % 360;
        wheel.setWheelRotation(current);
        float target = wheel.rotationFor(winner, offset);
        float delta = ((target - current) % 360 + 360) % 360;
        float end = current + (4 + random.nextInt(3)) * 360 + delta;

        spin.setEnabled(false);
        menuChoice.setEnabled(false);
        resultText.setText(R.string.meal_spinning);
        // Saved now, so the choice isn't lost if the screen is rotated during the spin.
        chooseMeal(meal);

        spinAnimator = ObjectAnimator.ofFloat(wheel, FoodWheelView.WHEEL_ROTATION, current, end);
        spinAnimator.setDuration(SPIN_MS);
        spinAnimator.setInterpolator(new DecelerateInterpolator(2f));
        spinAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spin.setEnabled(foods.size() >= MIN_FOODS);
                menuChoice.setEnabled(true);
                todaysMeal = meal;
                showResult();
            }
        });
        spinAnimator.start();
    }

    private void chooseMeal(String meal) {
        MealSelection selection = new MealSelection();
        selection.date = DataSetup.today();
        selection.food = meal;
        AppDatabase.write(() -> dao.save(selection));
    }

    /** Error message when the name is already on the menu (ignoring the food being renamed). */
    @Nullable
    private String duplicateError(String name, @Nullable Food except) {
        for (Food f : foods) {
            if (f != except && f.name.equalsIgnoreCase(name)) {
                return getString(R.string.error_duplicate);
            }
        }
        return null;
    }

    private void addFood() {
        if (foods.size() >= MAX_FOODS) {
            return;
        }
        Dialogs.editText(this, R.string.meal_add_food, R.string.meal_food_hint, null,
                name -> duplicateError(name, null),
                name -> {
                    Food food = new Food();
                    food.name = name;
                    AppDatabase.write(() -> {
                        food.position = dao.nextFoodPosition();
                        dao.insert(food);
                    });
                });
    }

    private void renameFood(Food food) {
        Dialogs.editText(this, R.string.meal_rename_food, R.string.meal_food_hint, food.name,
                name -> duplicateError(name, food),
                name -> {
                    Food copy = new Food();
                    copy.id = food.id;
                    copy.position = food.position;
                    copy.name = name;
                    String oldName = food.name;
                    AppDatabase.write(() -> {
                        dao.update(copy);
                        // Keep today's lunch pointing at the renamed food.
                        MealSelection today = dao.mealOnNow(DataSetup.today());
                        if (today != null && today.food.equals(oldName)) {
                            today.food = name;
                            dao.save(today);
                        }
                    });
                });
    }

    private void deleteFood(Food food) {
        Dialogs.confirmDelete(this, getString(R.string.meal_delete_food), food.name,
                () -> AppDatabase.write(() -> dao.delete(food)));
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
