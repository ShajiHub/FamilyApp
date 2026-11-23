package com.shajitha.familyapp;


import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import java.util.Random;

/** Kids Activities: suggests a random indoor or outdoor idea, and links to the Reading Log. */
public class ActivitiesFun extends BaseActivity {

    private static final String STATE_IDEA = "idea";
    private static final String STATE_CATEGORY = "category";

    private final Random random = new Random();
    private TextView ideaText;
    private TextView ideaCategory;
    private String lastIdea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activities_fun);

        ideaText = findViewById(R.id.textView8);
        ideaCategory = findViewById(R.id.ideaCategory);
        if (savedInstanceState != null && savedInstanceState.getString(STATE_IDEA) != null) {
            lastIdea = savedInstanceState.getString(STATE_IDEA);
            ideaText.setText(lastIdea);
            showCategory(savedInstanceState.getString(STATE_CATEGORY));
        }

        findViewById(R.id.indoorIdea).setOnClickListener(v -> {
            showIdea(R.array.indoor_activities);
            showCategory(getString(R.string.activities_indoor_label));
        });
        findViewById(R.id.outdoorIdea).setOnClickListener(v -> {
            showIdea(R.array.outdoor_activities);
            showCategory(getString(R.string.activities_outdoor_label));
        });
        findViewById(R.id.readingLog).setOnClickListener(v -> startActivity(new Intent(this, Reading.class)));
    }

    private void showIdea(int arrayRes) {
        String[] ideas = getResources().getStringArray(arrayRes);
        String idea;
        do {
            idea = ideas[random.nextInt(ideas.length)];
        } while (idea.equals(lastIdea) && ideas.length > 1); // don't repeat the same idea twice in a row
        lastIdea = idea;
        ideaText.setText(idea);
    }

    private void showCategory(String category) {
        ideaCategory.setText(category);
        ideaCategory.setVisibility(category == null ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_IDEA, lastIdea);
        outState.putString(STATE_CATEGORY, ideaCategory.getVisibility() == android.view.View.VISIBLE
                ? ideaCategory.getText().toString() : null);
    }
}
