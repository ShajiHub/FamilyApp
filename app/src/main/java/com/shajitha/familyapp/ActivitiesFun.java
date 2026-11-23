package com.shajitha.familyapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import java.util.Random;

/** Kids Activities: suggests a random indoor or outdoor idea, and links to the Reading Log. */
public class ActivitiesFun extends AppCompatActivity {

    private static final String STATE_IDEA = "idea";

    private final Random random = new Random();
    private TextView ideaText;
    private String lastIdea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activities_fun);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ideaText = findViewById(R.id.textView8);
        if (savedInstanceState != null && savedInstanceState.getString(STATE_IDEA) != null) {
            lastIdea = savedInstanceState.getString(STATE_IDEA);
            ideaText.setText(lastIdea);
        }

        findViewById(R.id.indoorIdea).setOnClickListener(v -> showIdea(R.array.indoor_activities));
        findViewById(R.id.outdoorIdea).setOnClickListener(v -> showIdea(R.array.outdoor_activities));
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_IDEA, lastIdea);
    }
}
